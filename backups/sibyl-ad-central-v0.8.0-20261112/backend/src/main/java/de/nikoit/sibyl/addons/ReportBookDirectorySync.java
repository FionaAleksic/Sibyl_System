package de.nikoit.sibyl.addons;

import de.nikoit.sibyl.addons.ad.SibylAdApi;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Only owns report-book roster persistence and trainer assignments.
 * Group mappings, LDAP searches, certificates and credentials live in Sibyl.ad.
 */
@Service
public class ReportBookDirectorySync {
    public record Result(int trainees,int trainers,int directories,String message) {}
    private record Flags(boolean trainee,boolean trainer) {}
    private final JdbcTemplate db;
    private final SibylAdApi directory;
    private final AuditService audit;
    public ReportBookDirectorySync(JdbcTemplate db,SibylAdApi directory,AuditService audit) {
        this.db=db;this.directory=directory;this.audit=audit;
    }
    @Transactional
    public Result synchronize(String actor) {
        SibylAdApi.GroupRoster snapshot=directory.groupRoster(Map.of(
            "trainee","reportBookTraineeGroups",
            "trainer","reportBookTrainerGroups"));
        Set<String> trainees=snapshot.usersByRole().getOrDefault("trainee",Set.of());
        Set<String> trainers=snapshot.usersByRole().getOrDefault("trainer",Set.of());
        if(trainees.isEmpty()&&trainers.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No matching accounts found in configured AD groups; roster unchanged");
        Map<String,Flags> detected=new LinkedHashMap<>();
        trainees.forEach(name->detected.put(name,new Flags(true,false)));
        trainers.forEach(name->detected.merge(name,new Flags(false,true),
            (a,b)->new Flags(a.trainee()||b.trainee(),a.trainer()||b.trainer())));
        // The transaction preserves manual profiles and existing assignments.
        db.update("UPDATE sibyl_report_profiles SET trainee_enabled=FALSE,trainer_enabled=FALSE,"
            +"active=FALSE WHERE source='AD'");
        detected.forEach((name,flags)->db.update(
            "INSERT INTO sibyl_report_profiles(username,trainee_enabled,trainer_enabled,active,source,last_synced_at) "
            +"VALUES(?,?,?,TRUE,'AD',CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE "
            +"trainee_enabled=IF(source='AD',VALUES(trainee_enabled),trainee_enabled),"
            +"trainer_enabled=IF(source='AD',VALUES(trainer_enabled),trainer_enabled),"
            +"active=IF(source='AD',TRUE,active),last_synced_at=CURRENT_TIMESTAMP",
            name,flags.trainee(),flags.trainer()));
        audit.record(actor,"REPORT_BOOK_DIRECTORY_SYNC",Integer.toString(detected.size()));
        return new Result(trainees.size(),trainers.size(),snapshot.directories(),
            "AD accounts synchronized; manual settings and trainer assignments preserved");
    }
}
