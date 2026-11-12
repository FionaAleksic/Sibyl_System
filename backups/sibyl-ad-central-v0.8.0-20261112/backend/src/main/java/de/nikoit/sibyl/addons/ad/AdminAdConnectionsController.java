package de.nikoit.sibyl.addons.ad;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ad-connections")
public class AdminAdConnectionsController {
    private final AdConnectionService directory;
    public AdminAdConnectionsController(AdConnectionService directory) {this.directory=directory;}
    @GetMapping public List<AdConnectionService.Connection> all() {return directory.all();}
    private SibylAdApi gateway;
    @org.springframework.beans.factory.annotation.Autowired
    public void setGateway(SibylAdApi gateway){this.gateway=gateway;}
    @GetMapping("/status") public SibylAdApi.Status status(){return gateway.status();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AdConnectionService.Connection create(@RequestBody AdConnectionService.Input x,Principal p) {
        return directory.create(x,p);
    }
    @PutMapping("/{id}")
    public AdConnectionService.Connection update(@PathVariable long id,@RequestBody AdConnectionService.Input x,Principal p) {
        return directory.update(id,x,p);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id,Principal p){directory.delete(id,p);}
    @PostMapping("/{id}/test")
    public AdConnectionService.TestResult test(@PathVariable long id,Principal p) {
        return directory.test(id,p);
    }
}
