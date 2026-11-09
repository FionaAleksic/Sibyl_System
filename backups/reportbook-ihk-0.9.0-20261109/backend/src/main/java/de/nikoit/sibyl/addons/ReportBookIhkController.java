package de.nikoit.sibyl.addons;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Familiar weekly day-by-day apprenticeship journal.
 * The v2 workflow remains the source of truth for status and approvals.
 * This API changes only the presentation/detail model. Every route checks
 * ownership or currently assigned trainer status on the server.
 */
@RestController
@RequestMapping("/api/v1/addons/Sibyl.report-book/v3")
public class ReportBookIhkController {
    public record Item(String kind,int minutes,String body) {}
    public record Day(String date,String attendance,String location,List<Item> entries) {}
    public record Detail(long id,String owner,String weekStart,String mode,String weeklySummary,
                         String status,String reviewer,String feedback,long revision,
                         String submittedAt,String reviewedAt,List<Day> days) {}
    public record Save(Long revision,String mode,String weeklySummary,List<Day> days) {}
    public record Comment(long id,String date,String author,String body,String at) {}
    public record CommentInput(String body) {}
    public record Attachment(long id,String date,String name,String type,int size,String uploadedBy,String at) {}
    public record Upload(String date,String name,String type,String base64) {}
    private record Access(String owner,LocalDate monday,String status,long revision) {}

    private final JdbcTemplate db;
    private final AddonRegistry addons;
    private final AuditService audit;
    private static final Set<String> PLACES=Set.of("BETRIEB","SCHULE","SCHULE_BETRIEB",
        "UNTERWEISUNG","SONSTIGES","URLAUB","KRANK","FEIERTAG");
    private static final Set<String> ATTENDANCE=Set.of("ANWESEND","ABWESEND","URLAUB","FEIERTAG",
        "ZEITAUSGLEICH","SONSTIGE_ABWESENHEIT","KRANK");
    private static final Set<String> TYPES=Set.of("TEXT","BULLET");
    private static final Set<String> FILE_TYPES=Set.of("application/pdf","image/png",
        "image/jpeg","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final int MAX_FILE_BYTES=3*1024*1024;

    public ReportBookIhkController(JdbcTemplate db,AddonRegistry addons,AuditService audit) {
        this.db=db;this.addons=addons;this.audit=audit;
    }
    private void check(Authentication user){addons.accessible("Sibyl.report-book",user);}
    private static void fail(HttpStatus code,String reason){
        throw new ResponseStatusException(code,reason);
    }
    private static String stamp(java.sql.Timestamp t){
        return t==null?null:t.toInstant().toString();
    }
    private static String text(String input,int max){
        if(input==null||input.length()>max)fail(HttpStatus.BAD_REQUEST,"Ungültiger Text");
        return input;
    }
    private static LocalDate date(String input){
        try{return LocalDate.parse(input);}
        catch(DateTimeParseException|NullPointerException ex){
            fail(HttpStatus.BAD_REQUEST,"Ungültiges Datum");return null;
        }
    }
    private Access access(long id,Authentication user,boolean lock,boolean write){
        Access a=db.query("SELECT owner_username,week_start,status,revision FROM sibyl_report_weeks "
            +"WHERE id=?"+(lock?" FOR UPDATE":""),
            r->r.next()?new Access(r.getString(1),r.getDate(2).toLocalDate(),
                r.getString(3),r.getLong(4)):null,id);
        if(a==null)fail(HttpStatus.NOT_FOUND,"Berichtswoche nicht gefunden");
        boolean owner=a.owner().equalsIgnoreCase(user.getName());
        if(write){
            if(!owner)fail(HttpStatus.FORBIDDEN,"Nur der eigene Bericht ist bearbeitbar");
            if(!Set.of("DRAFT","RETURNED").contains(a.status()))
                fail(HttpStatus.CONFLICT,"Bericht ist zur Prüfung gesperrt");
        }else if(!owner){
            boolean trainer=Boolean.TRUE.equals(db.query(
                "SELECT p.trainer_enabled AND p.active FROM sibyl_report_assignments a "
                +"JOIN sibyl_report_profiles p ON p.username=a.trainer_username "
                +"WHERE a.trainee_username=? AND a.trainer_username=?",
                r->r.next()?r.getBoolean(1):false,a.owner(),user.getName()));
            if(!trainer)fail(HttpStatus.FORBIDDEN,"Keine Berechtigung für dieses Berichtsheft");
        }
        return a;
    }
    private static void inWeek(Access a,LocalDate day){
        if(day.isBefore(a.monday())||day.isAfter(a.monday().plusDays(6)))
            fail(HttpStatus.BAD_REQUEST,"Datum liegt außerhalb der Berichtswoche");
    }
    private List<Day> days(long id,LocalDate monday){
        Map<LocalDate,Day> found=new LinkedHashMap<>();
        db.query("SELECT day_date,attendance,location_type FROM sibyl_report_days "
            +"WHERE week_id=? ORDER BY day_date",r->{
                LocalDate day=r.getDate(1).toLocalDate();
                found.put(day,new Day(day.toString(),r.getString(2),r.getString(3),new ArrayList<>()));
            },id);
        db.query("SELECT day_date,entry_kind,minutes_spent,entry_body "
            +"FROM sibyl_report_day_items WHERE week_id=? ORDER BY day_date,ordinal",r->{
                Day d=found.get(r.getDate(1).toLocalDate());
                if(d!=null)d.entries().add(new Item(r.getString(2),r.getInt(3),r.getString(4)));
            },id);
        List<Day> result=new ArrayList<>();
        for(int i=0;i<7;i++){
            LocalDate date=monday.plusDays(i);
            result.add(found.getOrDefault(date,new Day(date.toString(),"ANWESEND","BETRIEB",List.of())));
        }
        return result;
    }
    @GetMapping("/weeks/{id}/detail")
    public Detail detail(@PathVariable long id,Authentication user){
        check(user); Access a=access(id,user,false,false);
        return db.query("SELECT owner_username,week_start,report_mode,weekly_summary,status,"
            +"reviewer_username,review_feedback,revision,submitted_at,reviewed_at "
            +"FROM sibyl_report_weeks WHERE id=?",
            r->{
                if(!r.next()){fail(HttpStatus.NOT_FOUND,"Berichtswoche nicht gefunden");return null;}
                return new Detail(id,r.getString(1),r.getDate(2).toLocalDate().toString(),
                    r.getString(3),r.getString(4),r.getString(5),r.getString(6),
                    r.getString(7),r.getLong(8),stamp(r.getTimestamp(9)),
                    stamp(r.getTimestamp(10)),days(id,a.monday()));
            },id);
    }
    @PutMapping("/weeks/{id}/detail")
    @Transactional
    public Detail save(@PathVariable long id,@RequestBody Save input,Authentication user){
        check(user);Access a=access(id,user,true,true);
        if(input==null||input.revision()==null||input.revision()!=a.revision())
            fail(HttpStatus.CONFLICT,"Bericht wurde an anderer Stelle geändert – neu laden");
        if(!Set.of("DAILY","WEEKLY").contains(input.mode()))
            fail(HttpStatus.BAD_REQUEST,"Ungültige Berichtsform");
        String summary=text(input.weeklySummary()==null?"":input.weeklySummary(),16000);
        List<Day> lines=input.days()==null?List.of():input.days();
        if(lines.size()>7)fail(HttpStatus.BAD_REQUEST,"Höchstens sieben Tage");
        Set<LocalDate> used=new HashSet<>();
        record DayData(LocalDate day,String attendance,String location,List<Item> items,int total,String flat) {}
        List<DayData> checked=new ArrayList<>();
        for(Day line:lines){
            if(line==null)fail(HttpStatus.BAD_REQUEST,"Ungültiger Tagesbericht");
            LocalDate day=date(line.date()); inWeek(a,day);
            if(!used.add(day)||!ATTENDANCE.contains(line.attendance())
                ||!PLACES.contains(line.location()))
                fail(HttpStatus.BAD_REQUEST,"Ungültiger Tag, Ort oder Anwesenheit");
            List<Item> items=line.entries()==null?List.of():line.entries();
            if(items.size()>30)fail(HttpStatus.BAD_REQUEST,"Maximal 30 Einträge pro Tag");
            int sum=0;
            StringBuilder flat=new StringBuilder();
            for(Item item:items){
                if(item==null||!TYPES.contains(item.kind())||item.minutes()<0
                    ||item.minutes()>1440)fail(HttpStatus.BAD_REQUEST,"Ungültiger Tätigkeitseintrag");
                String body=text(item.body(),12000).trim();
                sum+=item.minutes();
                if(flat.length()+body.length()+3>16000)
                    fail(HttpStatus.BAD_REQUEST,"Tagesbericht zu lang");
                if(!body.isBlank()){
                    if(flat.length()>0)flat.append("\n");
                    flat.append(body);
                }
            }
            if(sum>1440)fail(HttpStatus.BAD_REQUEST,"Höchstens 24 Stunden pro Tag");
            checked.add(new DayData(day,line.attendance(),line.location(),items,sum,flat.toString()));
        }
        db.update("DELETE FROM sibyl_report_day_items WHERE week_id=?",id);
        db.update("DELETE FROM sibyl_report_days WHERE week_id=?",id);
        for(DayData day:checked){
            db.update("INSERT INTO sibyl_report_days(week_id,day_date,location_type,attendance,"
                +"minutes_spent,activity_text) VALUES(?,?,?,?,?,?)",
                id,java.sql.Date.valueOf(day.day()),day.location(),day.attendance(),
                day.total(),day.flat());
            for(int i=0;i<day.items().size();i++){
                Item item=day.items().get(i);
                db.update("INSERT INTO sibyl_report_day_items "
                    +"(week_id,day_date,ordinal,entry_kind,minutes_spent,entry_body)"
                    +" VALUES(?,?,?,?,?,?)",
                    id,java.sql.Date.valueOf(day.day()),i,item.kind(),
                    item.minutes(),item.body().trim());
            }
        }
        db.update("UPDATE sibyl_report_weeks SET report_mode=?,weekly_summary=?,"
            +"status='DRAFT',revision=revision+1 WHERE id=?",input.mode(),summary,id);
        audit.record(user.getName(),"REPORT_BOOK_SAVE",Long.toString(id));
        return detail(id,user);
    }
    @PostMapping("/weeks/{id}/withdraw")
    @Transactional
    public Detail withdraw(@PathVariable long id,Authentication user){
        check(user);Access a=access(id,user,true,false);
        if(!a.owner().equalsIgnoreCase(user.getName()))fail(HttpStatus.FORBIDDEN,"Nur der Azubi kann zurückziehen");
        if(!"SUBMITTED".equals(a.status()))
            fail(HttpStatus.CONFLICT,"Nur eingereichte Wochen können zurückgezogen werden");
        db.update("UPDATE sibyl_report_weeks SET status='DRAFT',submitted_at=NULL,"
            +"reviewed_at=NULL,reviewer_username=NULL,revision=revision+1 WHERE id=?",id);
        db.update("INSERT INTO sibyl_report_events(week_id,actor_username,event_type,note)"
            +" VALUES(?,?,'WITHDRAW',NULL)",id,user.getName());
        audit.record(user.getName(),"REPORT_BOOK_WITHDRAW",Long.toString(id));
        return detail(id,user);
    }
    @DeleteMapping("/weeks/{id}")
    @Transactional
    public void deleteWeek(@PathVariable long id,Authentication user){
        check(user);Access a=access(id,user,true,true);
        db.update("DELETE FROM sibyl_report_weeks WHERE id=?",id);
        audit.record(user.getName(),"REPORT_BOOK_DELETE_WEEK",
            Long.toString(id)+" "+a.monday().toString());
    }
    @GetMapping("/weeks/{id}/days/{when}/comments")
    public List<Comment> comments(@PathVariable long id,@PathVariable String when,Authentication user){
        check(user);Access a=access(id,user,false,false);
        LocalDate day=date(when);inWeek(a,day);
        return db.query("SELECT id,day_date,author_username,body,created_at "
            +"FROM sibyl_report_day_comments WHERE week_id=? AND day_date=? ORDER BY id",
            (r,i)->new Comment(r.getLong(1),r.getDate(2).toLocalDate().toString(),
                r.getString(3),r.getString(4),stamp(r.getTimestamp(5))),
            id,java.sql.Date.valueOf(day));
    }
    @PostMapping("/weeks/{id}/days/{when}/comments")
    @Transactional
    public void comment(@PathVariable long id,@PathVariable String when,
                        @RequestBody CommentInput input,Authentication user){
        check(user);Access a=access(id,user,false,false);
        LocalDate day=date(when);inWeek(a,day);
        String body=text(input==null?null:input.body(),1500).trim();
        if(body.isEmpty())fail(HttpStatus.BAD_REQUEST,"Kommentar darf nicht leer sein");
        db.update("INSERT INTO sibyl_report_day_comments"
            +"(week_id,day_date,author_username,body) VALUES(?,?,?,?)",
            id,java.sql.Date.valueOf(day),user.getName(),body);
        audit.record(user.getName(),"REPORT_BOOK_COMMENT",Long.toString(id));
    }
    @GetMapping("/weeks/{id}/files")
    public List<Attachment> files(@PathVariable long id,Authentication user){
        check(user);access(id,user,false,false);
        return db.query("SELECT id,day_date,file_name,mime_type,byte_size,uploaded_by,created_at "
            +"FROM sibyl_report_day_files WHERE week_id=? ORDER BY id",
            (r,i)->new Attachment(r.getLong(1),r.getDate(2).toLocalDate().toString(),
                r.getString(3),r.getString(4),r.getInt(5),r.getString(6),
                stamp(r.getTimestamp(7))),id);
    }
    @PostMapping("/weeks/{id}/files")
    @Transactional
    public void upload(@PathVariable long id,@RequestBody Upload request,Authentication user){
        check(user);Access a=access(id,user,true,true);
        if(request==null)fail(HttpStatus.BAD_REQUEST,"Datei fehlt");
        LocalDate day=date(request.date());inWeek(a,day);
        String name=text(request.name(),255).trim();
        String mime=text(request.type(),120);
        if(name.isEmpty()||name.contains("/")||name.contains("\\")||name.contains("\r")
            ||name.contains("\n")||!FILE_TYPES.contains(mime))
            fail(HttpStatus.BAD_REQUEST,"Dateiname oder Dateityp unzulässig");
        byte[] data;
        try{data=Base64.getDecoder().decode(text(request.base64(),MAX_FILE_BYTES*2));}
        catch(IllegalArgumentException ex){fail(HttpStatus.BAD_REQUEST,"Ungültige Dateidaten");return;}
        if(data.length==0||data.length>MAX_FILE_BYTES)fail(HttpStatus.BAD_REQUEST,"Datei maximal 3 MB");
        if(mime.equals("application/pdf")&&!magic(data,new byte[]{37,80,68,70}))
            fail(HttpStatus.BAD_REQUEST,"PDF-Datei ungültig");
        if(mime.equals("image/png")&&!magic(data,new byte[]{(byte)137,80,78,71}))
            fail(HttpStatus.BAD_REQUEST,"PNG-Datei ungültig");
        if(mime.equals("image/jpeg")&&!magic(data,new byte[]{(byte)255,(byte)216}))
            fail(HttpStatus.BAD_REQUEST,"JPEG-Datei ungültig");
        if(mime.endsWith("document")&&!magic(data,new byte[]{80,75}))
            fail(HttpStatus.BAD_REQUEST,"Office-Datei ungültig");
        Integer count=db.queryForObject("SELECT COUNT(*) FROM sibyl_report_day_files WHERE week_id=?",
            Integer.class,id);
        if(count!=null&&count>=40)fail(HttpStatus.BAD_REQUEST,"Maximal 40 Anhänge pro Woche");
        db.update("INSERT INTO sibyl_report_day_files"
            +"(week_id,day_date,uploaded_by,file_name,mime_type,byte_size,file_data)"
            +" VALUES(?,?,?,?,?,?,?)",id,java.sql.Date.valueOf(day),user.getName(),name,mime,
            data.length,data);
        audit.record(user.getName(),"REPORT_BOOK_UPLOAD",Long.toString(id));
    }
    private static boolean magic(byte[] data,byte[] pattern){
        if(data.length<pattern.length)return false;
        for(int i=0;i<pattern.length;i++)if(data[i]!=pattern[i])return false;
        return true;
    }
    @GetMapping("/weeks/{id}/files/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable long id,@PathVariable long fileId,Authentication user){
        check(user);access(id,user,false,false);
        return db.query("SELECT file_name,mime_type,file_data FROM sibyl_report_day_files "
            +"WHERE id=? AND week_id=?",r->{
                if(!r.next())fail(HttpStatus.NOT_FOUND,"Anhang nicht gefunden");
                String filename=r.getString(1),mime=r.getString(2);
                String safe=URLEncoder.encode(filename,StandardCharsets.UTF_8).replace("+","%20");
                return ResponseEntity.ok()
                    .header("Content-Disposition","attachment; filename=\"bericht-anlage\"; filename*=UTF-8''"+safe)
                    .header("X-Content-Type-Options","nosniff")
                    .contentType(MediaType.parseMediaType(mime))
                    .body(r.getBytes(3));
            },fileId,id);
    }
    @DeleteMapping("/weeks/{id}/files/{fileId}")
    @Transactional
    public void deleteFile(@PathVariable long id,@PathVariable long fileId,Authentication user){
        check(user);access(id,user,true,true);
        int changed=db.update("DELETE FROM sibyl_report_day_files WHERE id=? AND week_id=?",fileId,id);
        if(changed==0)fail(HttpStatus.NOT_FOUND,"Anhang nicht gefunden");
        audit.record(user.getName(),"REPORT_BOOK_DELETE_FILE",Long.toString(id));
    }
}
