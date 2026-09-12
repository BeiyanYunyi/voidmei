import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import voidmei.config.LegacySettingsReader;

/** Read-only inventory using the KMP importer, never the legacy application's action handlers. */
class LegacySettingsInventory {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: LegacySettingsInventory.java <legacy-layout.cfg>");
        var settings = LegacySettingsReader.INSTANCE.read(Files.readString(Path.of(args[0])));
        var targets = new HashSet<String>();
        for (var item : settings.getUnmigrated()) {
            targets.add(item.getTarget());
            System.out.println(item.getTarget() + "\t" + item.getLabel() + "\t" + String.join(" / ", item.getSourcePath()));
        }
        System.err.println("Unmigrated entries: " + settings.getUnmigrated().size() + "; unique identifiers: " + targets.size());
    }
}
