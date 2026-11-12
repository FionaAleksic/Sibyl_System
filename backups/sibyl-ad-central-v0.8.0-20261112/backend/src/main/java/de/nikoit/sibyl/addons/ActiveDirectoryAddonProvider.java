package de.nikoit.sibyl.addons;

import java.util.List;
import org.springframework.stereotype.Component;

/** All Active Directory configuration is owned by the compiled Sibyl.ad addon. */
@Component
public class ActiveDirectoryAddonProvider implements SibylAddonProvider {
    @Override public String type(){return "ad-auth";}
    @Override public List<AddonConfigurationService.Field> settings() {
        return List.of(
            new AddonConfigurationService.Field(
                "defaultDomain","Standarddomäne (optional)","text",
                "Für Anmeldung ohne @Domäne. Leer bedeutet: vorhandene Hauptdomäne oder einzige aktive Verbindung.",
                "",0,0,List.of()),
            new AddonConfigurationService.Field(
                "reportBookTraineeGroups","Azubi-Gruppen (Berichtsheft)","text",
                "AD-Gruppennamen oder vollständige Gruppen-DNs, eine Gruppe pro Zeile; nicht firmenspezifisch.",
                "",0,0,List.of()),
            new AddonConfigurationService.Field(
                "reportBookTrainerGroups","Ausbilder-Gruppen (Berichtsheft)","text",
                "AD-Gruppennamen oder vollständige Gruppen-DNs, eine Gruppe pro Zeile.",
                "",0,0,List.of())
        );
    }
}
