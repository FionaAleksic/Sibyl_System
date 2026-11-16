package de.nikoit.sibyl.addons;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Training-year and AD-group aware, many-to-many trainer assignments.
 * This API never exposes inactive accounts and all modifications require
 * server-verified Sibyl administrators.
 */
@RestController
@RequestMapping("/api/v1/addons/Sibyl.report-book/v2/admin")
public class ReportBookAssignmentAdminController {
    public record Profile(String username,boolean trainee,boolean trainer,
            Integer learningYear,List<String> groups,List<String> assignedTrainers) {}
    public record SetYear(Integer year) {}
    public record SetGroup(String group) {}
    public record BulkYear(String group,Integer year) {}
    public record BulkAssign(Integer year,String group,List<String> trainers) {}
    public record BulkResult(int matched,int assignmentsAdded,int alreadyAssignedOrSelf) {}

    private final JdbcTemplate db;
    private final AddonRegistry registry;
    private final AuditService audit;
    public ReportBookAssignmentAdminController(JdbcTemplate db,AddonRegistry registry,AuditService audit){
        this.db=db;this.registry=registry;this.audit=audit;
    }
    private void admin(Authentication auth){
        if(!registry.isAdmin(auth))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administratorrechte erforderlich");
    }
    private static ResponseStatusException bad(String reason){
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);
    }
    private static String account(String value){
        if(value==null||!value.matches("(?i)[a-z0-9][a-z0-9._@-]{0,99}"))
            throw bad("Ungültiger Benutzername");
        return value.toLowerCase(Locale.ROOT);
    }
    private static String group(String value){
        if(value==null||!value.matches("[\\p{L}0-9 ._-]{1,100}"))
            throw bad("Ungültiger Gruppenname");
        return value.trim();
    }
    private static void checkYear(Integer year){
        if(year!=null&&(year<1||year>4))throw bad("Lehrjahr muss zwischen 1 und 4 liegen");
    }
    private static List<String> split(String csv){
        if(csv==null||csv.isBlank())return List.of();
        return List.of(csv.split("\\|"));
    }
    private boolean eligible(String name,boolean trainee){
        return Boolean.TRUE.equals(db.query(
            "SELECT active AND "+(trainee?"trainee_enabled":"trainer_enabled")
            +" FROM sibyl_report_profiles WHERE username=?",
            r->r.next()?r.getBoolean(1):false,name));
    }
    private static final String PROFILES_SQL=
         "SELECT p.username,p.trainee_enabled,p.trainer_enabled,p.learning_year,"
        +"(SELECT GROUP_CONCAT(g.group_name ORDER BY g.group_name SEPARATOR '|') "
        +" FROM sibyl_report_profile_groups g WHERE g.username=p.username),"
        +"(SELECT GROUP_CONCAT(a.trainer_username ORDER BY a.trainer_username SEPARATOR '|') "
        +" FROM sibyl_report_assignments a WHERE a.trainee_username=p.username) "
        +"FROM sibyl_report_profiles p WHERE p.active=TRUE ORDER BY p.username";

    @GetMapping("/roster-v3")
    public List<Profile> roster(Authentication auth) {
        admin(auth);
        return db.query(PROFILES_SQL,(r,i)->new Profile(r.getString(1),r.getBoolean(2),
            r.getBoolean(3),(Integer)r.getObject(4),split(r.getString(5)),
            split(r.getString(6))));
    }
    @PutMapping("/trainees/{username}/year")
    @Transactional
    public void year(@PathVariable String username,@RequestBody SetYear data,Authentication auth){
        admin(auth);
        String name=account(username);
        if(data==null)throw bad("Lehrjahr fehlt");
        checkYear(data.year());
        if(!eligible(name,true))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Aktives Azubi-Konto erforderlich");
        db.update("UPDATE sibyl_report_profiles SET learning_year=? WHERE username=?",
            data.year(),name);
        audit.record(auth.getName(),"REPORT_BOOK_LEARNING_YEAR",name);
    }
    @PutMapping("/trainees/{username}/group")
    @Transactional
    public void setGroup(@PathVariable String username,@RequestBody SetGroup data,Authentication auth){
        admin(auth);
        String name=account(username);
        if(data==null)throw bad("Gruppe fehlt");
        if(!eligible(name,true))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Aktives Azubi-Konto erforderlich");
        String label=data.group()==null||data.group().isBlank()?null:group(data.group());
        db.update("DELETE FROM sibyl_report_profile_groups WHERE username=? AND source='MANUAL'",name);
        if(label!=null)db.update("INSERT INTO sibyl_report_profile_groups(username,group_name,source) "
            +"VALUES(?,?,'MANUAL') ON DUPLICATE KEY UPDATE source=source",name,label);
        audit.record(auth.getName(),"REPORT_BOOK_GROUP_OVERRIDE",name);
    }
    @PostMapping("/trainees/bulk-year")
    @Transactional
    public int bulkYear(@RequestBody BulkYear data,Authentication auth){
        admin(auth);
        if(data==null||data.year()==null)throw bad("Lehrjahr ist erforderlich");
        checkYear(data.year());
        String label=group(data.group());
        int count=db.update("UPDATE sibyl_report_profiles p SET p.learning_year=? "
            +"WHERE p.active=TRUE AND p.trainee_enabled=TRUE AND EXISTS("
            +"SELECT 1 FROM sibyl_report_profile_groups g WHERE g.username=p.username "
            +"AND g.group_name=?)",data.year(),label);
        audit.record(auth.getName(),"REPORT_BOOK_BULK_YEAR",label+":"+count);
        return count;
    }
    @PutMapping("/assignments/{trainee}/{trainer}")
    @Transactional
    public void add(@PathVariable String trainee,@PathVariable String trainer,Authentication auth){
        admin(auth);
        String student=account(trainee),teacher=account(trainer);
        if(student.equals(teacher))throw bad("Selbstprüfung ist nicht erlaubt");
        if(!eligible(student,true)||!eligible(teacher,false))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Aktive Azubi- und Ausbilderkonten erforderlich");
        db.update("INSERT INTO sibyl_report_assignments(trainee_username,trainer_username,assigned_by) "
            +"VALUES(?,?,?) ON DUPLICATE KEY UPDATE assigned_by=assigned_by",
            student,teacher,auth.getName());
        audit.record(auth.getName(),"REPORT_BOOK_ASSIGN",student);
    }
    @DeleteMapping("/assignments/{trainee}/{trainer}")
    @Transactional
    public void remove(@PathVariable String trainee,@PathVariable String trainer,Authentication auth){
        admin(auth);
        String student=account(trainee),teacher=account(trainer);
        db.update("DELETE FROM sibyl_report_assignments WHERE trainee_username=? AND trainer_username=?",
            student,teacher);
        audit.record(auth.getName(),"REPORT_BOOK_UNASSIGN",student);
    }
    @PostMapping("/assignments/bulk")
    @Transactional
    public BulkResult bulk(@RequestBody BulkAssign data,Authentication auth){
        admin(auth);
        if(data==null||(data.year()==null&&(data.group()==null||data.group().isBlank())))
            throw bad("Mindestens Gruppe oder Lehrjahr auswählen");
        checkYear(data.year());
        if(data.trainers()==null||data.trainers().isEmpty()||data.trainers().size()>20)
            throw bad("1 bis 20 Ausbilder auswählen");
        String label=data.group()==null||data.group().isBlank()?null:group(data.group());
        List<String> selected=data.trainers().stream().map(ReportBookAssignmentAdminController::account)
            .distinct().toList();
        for(String trainer:selected)
            if(!eligible(trainer,false))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Ausbilderkonto ist nicht aktiv");
        String sql="SELECT p.username FROM sibyl_report_profiles p WHERE p.active=TRUE "
           +"AND p.trainee_enabled=TRUE"
           +(data.year()!=null?" AND p.learning_year=?":"")
           +(label!=null?" AND EXISTS(SELECT 1 FROM sibyl_report_profile_groups g "
             +"WHERE g.username=p.username AND g.group_name=?)":"")
           +" ORDER BY p.username LIMIT 1001";
        List<Object> params=new ArrayList<>();
        if(data.year()!=null)params.add(data.year());
        if(label!=null)params.add(label);
        List<String> students=db.queryForList(sql,String.class,params.toArray());
        if(students.size()>1000)throw bad("Zu viele Azubis für eine Sammelzuweisung");
        int added=0,skipped=0;
        for(String student:students){
            for(String teacher:selected){
                if(student.equals(teacher)){skipped++;continue;}
                int result=db.update("INSERT IGNORE INTO sibyl_report_assignments"
                   +"(trainee_username,trainer_username,assigned_by) VALUES(?,?,?)",
                   student,teacher,auth.getName());
                if(result==1)added++;else skipped++;
            }
        }
        audit.record(auth.getName(),"REPORT_BOOK_BULK_ASSIGN",
            "trainees="+students.size()+",added="+added);
        return new BulkResult(students.size(),added,skipped);
    }
}
