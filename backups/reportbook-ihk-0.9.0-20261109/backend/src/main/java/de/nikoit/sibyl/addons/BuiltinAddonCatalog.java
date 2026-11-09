package de.nikoit.sibyl.addons;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sibyl ships a closed catalogue of compiled addons.
 *
 * Nothing is downloaded or executed from Git. The database stores only
 * administrator-controlled enable flags and settings, not executable modules.
 * Reconciliation never changes the current enabled flag.
 */
@Component
public class BuiltinAddonCatalog implements ApplicationRunner {
    public record Definition(String id,String title,String version,String summary,
                             String type,String documentMode,boolean enabledByDefault,
                             boolean allowUsers) {}
    private static final List<Definition> DEFINITIONS=List.of(
        new Definition("Sibyl.ad","Active Directory","0.7.1",
            "Mehrere Active-Directory-Verbindungen, LDAPS-Anmeldung und verschluesselte Servicekonten.",
            "ad-auth","personal",true,false),
        new Definition("Sibyl.inventory","Inventar / Erkennung","0.7.1",
            "Verwaltung und automatische Erkennung von AD-Controllern und bekannten Netzwerkgeraeten.",
            "inventory-discovery","personal",true,false),
        new Definition("Sibyl.notes","Meine Notizen","0.7.1",
            "Persoenliche Notizen in der Sibyl-Datenbank.","documents","personal",true,true),
        new Definition("Sibyl.doc","Wiki / Dokumentation","0.7.1",
            "Gemeinsame Wissensartikel mit zentraler Administrationskonfiguration.",
            "documents","shared",false,true),
        new Definition("Sibyl.planning","Planung","0.7.1",
            "Drei getrennte Planungsrotationen mit konfigurierbaren Regeln.",
            "planning","personal",false,true),
        new Definition("Sibyl.report-book","Berichtsheft","0.9.0",
            "Wochenberichte mit Einreichung, Ausbilderzuordnung, Rueckgabe und Freigabe.",
            "report-book","personal",false,true)
    );
    private static final Set<String> IDS=DEFINITIONS.stream()
        .map(Definition::id).collect(Collectors.toUnmodifiableSet());
    public static boolean isBundled(String id){return IDS.contains(id);}
    public static List<Definition> definitions(){return DEFINITIONS;}

    private final JdbcTemplate db;
    private final List<SibylAddonProvider> providers;
    public BuiltinAddonCatalog(JdbcTemplate db,List<SibylAddonProvider> providers){
        this.db=db;this.providers=providers;
    }
    @Override public void run(ApplicationArguments args){
        for(Definition addon:DEFINITIONS){
            if(providers.stream().noneMatch(p->p.type().equals(addon.type())))
                throw new IllegalStateException("Built-in provider is missing: "+addon.type());
            db.update("INSERT INTO sibyl_addons"
                +"(addon_id,name,version,description,module_type,document_mode,enabled,allow_users) "
                +"VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                +"name=VALUES(name),version=VALUES(version),description=VALUES(description),"
                +"module_type=VALUES(module_type),document_mode=VALUES(document_mode),"
                +"allow_users=VALUES(allow_users)",
                addon.id(),addon.title(),addon.version(),addon.summary(),
                addon.type(),addon.documentMode(),addon.enabledByDefault(),addon.allowUsers());
        }
    }
}
