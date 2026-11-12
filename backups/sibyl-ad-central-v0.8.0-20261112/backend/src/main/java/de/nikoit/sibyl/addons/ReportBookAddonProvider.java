package de.nikoit.sibyl.addons;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class ReportBookAddonProvider implements SibylAddonProvider {
 @Override public String type(){return "report-book";}
 @Override public List<AddonConfigurationService.Field> settings(){
  return List.of(
   new AddonConfigurationService.Field("minMinutes","Minuten je altem Eintrag (Minimum)","number",
       "Kompatibilität mit der vorherigen Einzel-Eintrag-Ansicht.","15",1,1440,List.of()),
   new AddonConfigurationService.Field("maxMinutes","Minuten je altem Eintrag (Maximum)","number",
       "Kompatibilität mit der vorherigen Einzel-Eintrag-Ansicht.","600",1,1440,List.of()),
   new AddonConfigurationService.Field("allowFutureEntries","Zukünftige Einträge erlauben","boolean",
       "Kompatibilität mit dem bisherigen Berichtsheft.","false",0,0,List.of()));
 }
}
