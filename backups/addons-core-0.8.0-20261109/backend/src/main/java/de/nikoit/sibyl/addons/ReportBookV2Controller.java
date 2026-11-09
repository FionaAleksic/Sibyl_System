package de.nikoit.sibyl.addons;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Weekly training records. Authorization is enforced for every request in the
 * backend, never by trusting the role or owner sent by the browser.
 */
@RestController
@RequestMapping("/api/v1/addons/Sibyl.report-book/v2")
public class ReportBookV2Controller {
    public record Day(String date,String location,String attendance,int minutes,String activity) {}
    public record Week(long id,String owner,String weekStart,String mode,String weeklySummary,
                       String status,String reviewer,String feedback,long revision,
                       String submittedAt,String reviewedAt,String updatedAt,List<Day> days) {}
    public record CreateWeek(String weekStart,String mode) {}
    public record SaveWeek(Long revision,String mode,String weeklySummary,List<Day> days) {}
    public record Review(String decision,String feedback) {}
    public record Profile(String username,boolean trainee,boolean trainer,boolean active,
                          String source,String assignedTrainer) {}
    public record SetProfile(Boolean trainee,Boolean trainer,Boolean active) {}
    public record SetAssignment(String trainerUsername) {}
    public record Me(String username,boolean trainee,boolean trainer,String assignedTrainer,boolean admin) {}
    public record Event(String actor,String type,String note,String at) {}

    private static final Set<String> LOCATIONS=Set.of("BETRIEB","SCHULE","UNTERWEISUNG","URLAUB",
        "KRANK","FEIERTAG","SONSTIGES");
    private static final Set<String> ATTENDANCE=Set.of("ANWESEND","ABWESEND");
    private final JdbcTemplate db;
    private final AddonRegistry addons;
    private final AuditService audit;
    private final ReportBookDirectorySync directory;

    public ReportBookV2Controller(JdbcTemplate db,AddonRegistry addons,AuditService audit,
                                  ReportBookDirectorySync directory) {
        this.db=db;this.addons=addons;this.audit=audit;this.directory=directory;
    }
    private void check(Authentication auth) { addons.accessible("Sibyl.report-book",auth); }
    private void administrator(Authentication auth) {
        // Administrators can prepare addon configuration while the module is disabled.
        // Functional end-user endpoints still require an enabled addon.
        if (!addons.isAdmin(auth)) fail(HttpStatus.FORBIDDEN,"Administrator required");
    }
    private static void fail(HttpStatus status,String message) {
        throw new ResponseStatusException(status,message);
    }
    private static String clean(String text,int max) {
        if(text==null||text.length()>max) { fail(HttpStatus.BAD_REQUEST,"Invalid text"); }
        return text.trim();
    }
    private static String timestamp(java.sql.Timestamp time) {
        return time==null?null:time.toInstant().toString();
    }
    private LocalDate parseWeek(String value) {
        LocalDate date;
        try { date=LocalDate.parse(value); }
        catch(DateTimeParseException|NullPointerException e) {
            fail(HttpStatus.BAD_REQUEST,"Invalid ISO week date");return null;
        }
        if(date.getDayOfWeek()!=DayOfWeek.MONDAY || date.isBefore(LocalDate.now().minusYears(10))
            || date.isAfter(LocalDate.now().plusYears(2)))
            fail(HttpStatus.BAD_REQUEST,"Week start must be a valid Monday");
        return date;
    }
    private String mode(String text) {
        if(!List.of("DAILY","WEEKLY").contains(text)) fail(HttpStatus.BAD_REQUEST,"Invalid report mode");
        return text;
    }
    private final String select="SELECT id,owner_username,week_start,report_mode,weekly_summary,status,"
        +"reviewer_username,review_feedback,revision,submitted_at,reviewed_at,updated_at "
        +"FROM sibyl_report_weeks ";
    private Week fromRow(java.sql.ResultSet r,boolean withDays) throws java.sql.SQLException {
        long id=r.getLong(1);
        return new Week(id,r.getString(2),r.getDate(3).toLocalDate().toString(),r.getString(4),
            r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getLong(9),
            timestamp(r.getTimestamp(10)),timestamp(r.getTimestamp(11)),timestamp(r.getTimestamp(12)),
            withDays?days(id):List.of());
    }
    private List<Day> days(long weekId) {
        return db.query("SELECT day_date,location_type,attendance,minutes_spent,activity_text "
            +"FROM sibyl_report_days WHERE week_id=? ORDER BY day_date",
            (r,i)->new Day(r.getDate(1).toLocalDate().toString(),r.getString(2),r.getString(3),
                r.getInt(4),r.getString(5)),weekId);
    }
    private Week find(long id,boolean forUpdate) {
        return db.query(select+"WHERE id=?"+(forUpdate?" FOR UPDATE":""),
            r->{if(!r.next()) fail(HttpStatus.NOT_FOUND,"Report week not found");
                 return fromRow(r,true);},id);
    }
    private boolean isOwner(Week w,Authentication auth) {
        return w.owner().equalsIgnoreCase(auth.getName());
    }
    private boolean isTrainer(Authentication auth) {
        return Boolean.TRUE.equals(db.query("SELECT trainer_enabled AND active "
            +"FROM sibyl_report_profiles WHERE username=?",r->r.next()?r.getBoolean(1):false,auth.getName()));
    }
    private boolean isTrainee(Authentication auth) {
        return Boolean.TRUE.equals(db.query("SELECT trainee_enabled AND active "
            +"FROM sibyl_report_profiles WHERE username=?",r->r.next()?r.getBoolean(1):false,auth.getName()));
    }
    private String assigned(String trainee) {
        return db.query("SELECT trainer_username FROM sibyl_report_assignments WHERE trainee_username=?",
            r->r.next()?r.getString(1):null,trainee);
    }
    private boolean assignedTo(Week w,Authentication auth) {
        return isTrainer(auth) && auth.getName().equalsIgnoreCase(assigned(w.owner()));
    }
    private void canRead(Week w,Authentication auth) {
        if(!isOwner(w,auth) && !assignedTo(w,auth))
            fail(HttpStatus.FORBIDDEN,"No access to this report");
    }
    private void canEdit(Week w,Authentication auth) {
        if(!isOwner(w,auth) || !Set.of("DRAFT","RETURNED").contains(w.status()))
            fail(HttpStatus.CONFLICT,"This report is not editable");
    }
    private void event(long id,String actor,String type,String note) {
        db.update("INSERT INTO sibyl_report_events(week_id,actor_username,event_type,note) VALUES(?,?,?,?)",
            id,actor,type,note);
        audit.record(actor,"REPORT_BOOK_"+type,Long.toString(id));
    }
    private static final String profileSql="SELECT p.username,p.trainee_enabled,p.trainer_enabled,"
        +"p.active,p.source,a.trainer_username FROM sibyl_report_profiles p "
        +"LEFT JOIN sibyl_report_assignments a ON a.trainee_username=p.username ";
    private List<Profile> profiles(String where,Object...args) {
        return db.query(profileSql+where,(r,i)->new Profile(r.getString(1),r.getBoolean(2),
            r.getBoolean(3),r.getBoolean(4),r.getString(5),r.getString(6)),args);
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        check(auth);
        return new Me(auth.getName(),isTrainee(auth),isTrainer(auth),assigned(auth.getName()),
            addons.isAdmin(auth));
    }
    @GetMapping("/weeks")
    public List<Week> ownWeeks(Authentication auth) {
        check(auth);
        return db.query(select+"WHERE owner_username=? ORDER BY week_start DESC LIMIT 250",
            (r,i)->fromRow(r,false),auth.getName());
    }
    @GetMapping("/weeks/{id}")
    public Week week(@PathVariable long id,Authentication auth) {
        check(auth); Week w=find(id,false); canRead(w,auth);return w;
    }
    @GetMapping("/review-queue")
    public List<Week> reviewQueue(Authentication auth) {
        check(auth);
        if(!isTrainer(auth)) fail(HttpStatus.FORBIDDEN,"Trainer role required");
        return db.query(select+"WHERE owner_username IN "
            +"(SELECT trainee_username FROM sibyl_report_assignments WHERE trainer_username=?) "
            +"ORDER BY CASE WHEN status='SUBMITTED' THEN 0 ELSE 1 END,week_start DESC LIMIT 500",
            (r,i)->fromRow(r,false),auth.getName());
    }
    @GetMapping("/weeks/{id}/history")
    public List<Event> history(@PathVariable long id,Authentication auth) {
        check(auth);canRead(find(id,false),auth);
        return db.query("SELECT actor_username,event_type,note,created_at FROM sibyl_report_events "
            +"WHERE week_id=? ORDER BY id DESC",
            (r,i)->new Event(r.getString(1),r.getString(2),r.getString(3),
                timestamp(r.getTimestamp(4))),id);
    }
    @PostMapping("/weeks")
    @Transactional
    public Week create(@RequestBody CreateWeek input,Authentication auth) {
        check(auth);
        if(input==null) fail(HttpStatus.BAD_REQUEST,"Missing week");
        LocalDate start=parseWeek(input.weekStart());String format=mode(input.mode());
        db.update("INSERT INTO sibyl_report_weeks(owner_username,week_start,report_mode) VALUES(?,?,?)",
            auth.getName(),java.sql.Date.valueOf(start),format);
        Long id=db.queryForObject("SELECT id FROM sibyl_report_weeks "
            +"WHERE owner_username=? AND week_start=?",Long.class,auth.getName(),java.sql.Date.valueOf(start));
        event(id,auth.getName(),"CREATE",null);
        return find(id,false);
    }
    @PutMapping("/weeks/{id}")
    @Transactional
    public Week save(@PathVariable long id,@RequestBody SaveWeek input,Authentication auth) {
        check(auth);
        Week old=find(id,true);canEdit(old,auth);
        if(input==null||input.revision()==null||input.revision()!=old.revision())
            fail(HttpStatus.CONFLICT,"Report was changed elsewhere; reload");
        String format=mode(input.mode());
        String summary=clean(input.weeklySummary()==null?"":input.weeklySummary(),16000);
        List<Day> inputDays=input.days()==null?List.of():input.days();
        if(inputDays.size()>7) fail(HttpStatus.BAD_REQUEST,"Maximum seven days");
        LocalDate monday=LocalDate.parse(old.weekStart());
        Set<LocalDate> used=new HashSet<>();
        for(Day d:inputDays) {
            if(d==null) fail(HttpStatus.BAD_REQUEST,"Invalid day");
            LocalDate date;
            try{date=LocalDate.parse(d.date());}
            catch(Exception ex){fail(HttpStatus.BAD_REQUEST,"Invalid day date");return old;}
            if(date.isBefore(monday)||date.isAfter(monday.plusDays(6))||!used.add(date)
                ||!LOCATIONS.contains(d.location())||!ATTENDANCE.contains(d.attendance())
                ||d.minutes()<0||d.minutes()>1440)
                fail(HttpStatus.BAD_REQUEST,"Invalid day data");
            clean(d.activity(),16000);
        }
        db.update("DELETE FROM sibyl_report_days WHERE week_id=?",id);
        for(Day d:inputDays)
            db.update("INSERT INTO sibyl_report_days(week_id,day_date,location_type,attendance,"
                +"minutes_spent,activity_text) VALUES(?,?,?,?,?,?)",
                id,java.sql.Date.valueOf(d.date()),d.location(),d.attendance(),d.minutes(),
                d.activity().trim());
        db.update("UPDATE sibyl_report_weeks SET report_mode=?,weekly_summary=?,status='DRAFT',"
            +"revision=revision+1 WHERE id=?",
            format,summary,id);
        event(id,auth.getName(),"SAVE",null);
        return find(id,false);
    }
    @PostMapping("/weeks/{id}/submit")
    @Transactional
    public Week submit(@PathVariable long id,Authentication auth) {
        check(auth);Week w=find(id,true);canEdit(w,auth);
        if(!isTrainee(auth)) fail(HttpStatus.FORBIDDEN,"Trainee group required to submit");
        String trainer=assigned(auth.getName());
        if(trainer==null || trainer.equalsIgnoreCase(auth.getName()))
            fail(HttpStatus.CONFLICT,"A trainer must be assigned first");
        String validTrainer=db.query("SELECT username FROM sibyl_report_profiles WHERE username=? "
            +"AND trainer_enabled=TRUE AND active=TRUE",r->r.next()?r.getString(1):null,trainer);
        if(validTrainer==null) fail(HttpStatus.CONFLICT,"Assigned trainer is unavailable");
        boolean valid="WEEKLY".equals(w.mode()) ? w.weeklySummary()!=null&&!w.weeklySummary().isBlank()
            : w.days().stream().anyMatch(d->!d.activity().isBlank()||d.minutes()>0);
        if(!valid) fail(HttpStatus.BAD_REQUEST,"Complete the report before submitting");
        db.update("UPDATE sibyl_report_weeks SET status='SUBMITTED',review_feedback=NULL,"
            +"reviewer_username=NULL,reviewed_at=NULL,submitted_at=CURRENT_TIMESTAMP,"
            +"revision=revision+1 WHERE id=?",id);
        event(id,auth.getName(),"SUBMIT",null);
        return find(id,false);
    }
    @PostMapping("/weeks/{id}/review")
    @Transactional
    public Week review(@PathVariable long id,@RequestBody Review input,Authentication auth) {
        check(auth);Week w=find(id,true);
        if(isOwner(w,auth)||!assignedTo(w,auth))
            fail(HttpStatus.FORBIDDEN,"Not assigned as this trainee's trainer");
        if(!"SUBMITTED".equals(w.status()))
            fail(HttpStatus.CONFLICT,"Report has not been submitted");
        if(input==null||!Set.of("APPROVED","RETURNED").contains(input.decision()))
            fail(HttpStatus.BAD_REQUEST,"Invalid review decision");
        String feedback=clean(input.feedback()==null?"":input.feedback(),1500);
        if("RETURNED".equals(input.decision())&&feedback.isBlank())
            fail(HttpStatus.BAD_REQUEST,"A reason is required");
        db.update("UPDATE sibyl_report_weeks SET status=?,reviewer_username=?,review_feedback=?,"
            +"reviewed_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=?",
            input.decision(),auth.getName(),feedback,id);
        event(id,auth.getName(),input.decision(),feedback);
        return find(id,false);
    }

    @GetMapping("/admin/roster")
    public List<Profile> roster(Authentication auth) {
        administrator(auth);
        return profiles("ORDER BY p.username");
    }
    @PutMapping("/admin/roster/{username}")
    @Transactional
    public void setProfile(@PathVariable String username,@RequestBody SetProfile data,Authentication auth) {
        administrator(auth);
        String name=normalize(username);
        if(data==null||data.trainee()==null||data.trainer()==null||data.active()==null)
            fail(HttpStatus.BAD_REQUEST,"Incomplete profile");
        db.update("INSERT INTO sibyl_report_profiles(username,trainee_enabled,trainer_enabled,active,source) "
            +"VALUES(?,?,?,?,'MANUAL') ON DUPLICATE KEY UPDATE trainee_enabled=VALUES(trainee_enabled),"
            +"trainer_enabled=VALUES(trainer_enabled),active=VALUES(active),source='MANUAL'",
            name,data.trainee(),data.trainer(),data.active());
        audit.record(auth.getName(),"REPORT_BOOK_PROFILE",name);
    }
    @PutMapping("/admin/assignment/{username}")
    @Transactional
    public void assign(@PathVariable String username,@RequestBody SetAssignment data,Authentication auth) {
        administrator(auth);
        String trainee=normalize(username);
        if(data==null||data.trainerUsername()==null)
            fail(HttpStatus.BAD_REQUEST,"Trainer required");
        String trainer=normalize(data.trainerUsername());
        if(trainee.equals(trainer)) fail(HttpStatus.BAD_REQUEST,"Self review is prohibited");
        if(!Boolean.TRUE.equals(db.query("SELECT trainee_enabled AND active FROM sibyl_report_profiles WHERE username=?",
            r->r.next()?r.getBoolean(1):false,trainee))
            || !Boolean.TRUE.equals(db.query("SELECT trainer_enabled AND active FROM sibyl_report_profiles WHERE username=?",
            r->r.next()?r.getBoolean(1):false,trainer)))
            fail(HttpStatus.CONFLICT,"Both profiles must be active and correctly classified");
        db.update("INSERT INTO sibyl_report_assignments(trainee_username,trainer_username,assigned_by) "
            +"VALUES(?,?,?) ON DUPLICATE KEY UPDATE trainer_username=VALUES(trainer_username),"
            +"assigned_by=VALUES(assigned_by),assigned_at=CURRENT_TIMESTAMP",
            trainee,trainer,auth.getName());
        audit.record(auth.getName(),"REPORT_BOOK_ASSIGN",trainee);
    }
    @DeleteMapping("/admin/assignment/{username}")
    @Transactional
    public void unassign(@PathVariable String username,Authentication auth) {
        administrator(auth);
        db.update("DELETE FROM sibyl_report_assignments WHERE trainee_username=?",normalize(username));
        audit.record(auth.getName(),"REPORT_BOOK_UNASSIGN",username);
    }
    @PostMapping("/admin/sync")
    public ReportBookDirectorySync.Result sync(Authentication auth) {
        administrator(auth);
        return directory.synchronize(auth.getName());
    }
    private static String normalize(String username) {
        if(username==null||!username.matches("(?i)[a-z0-9][a-z0-9._@-]{0,99}"))
            fail(HttpStatus.BAD_REQUEST,"Invalid account name");
        return username.toLowerCase(Locale.ROOT);
    }
}
