package de.nikoit.sibyl.common;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiExceptionHandler {
 // Show explicit, application-authored reasons rather than generic "Conflict".
 // Do not expose exception causes, LDAP objects, service passwords or stack traces.
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<Map<String,String>> status(ResponseStatusException e){
   String reason=e.getReason();
   if(reason==null || reason.isBlank())reason="Die Anfrage konnte nicht verarbeitet werden.";
   return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",reason));
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<Map<String,String>> duplicate(DataIntegrityViolationException e){
  return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Datensatz steht in Konflikt mit bestehender Zuordnung"));
 }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 public ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException e){
  String msg=e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).collect(Collectors.joining(", "));
  return ResponseEntity.badRequest().body(Map.of("error",msg));
 }
}
