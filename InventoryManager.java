import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local equipment tracker. Requires Java 17 or newer; no external libraries. */
public class InventoryManager {
    static final List<String> CONDITIONS = List.of("Good", "Needs Repair", "Retired");

    // Each device is one object. Its serial number is the unique inventory key.
    static class Device {
        final String serial;
        String brand, model, os, assignedTo, condition;
        Device(String serial, String brand, String model, String os, String assignedTo, String condition) {
            this.serial = required(serial); this.brand = required(brand);
            this.model = required(model); this.os = required(os);
            this.assignedTo = assignedTo.trim(); this.condition = condition;
            if (!CONDITIONS.contains(condition)) throw new IllegalArgumentException("Invalid condition.");
        }
        String availability() { return !condition.equals("Good") ? "Out of Service" : (assignedTo.isEmpty() ? "Available" : "Assigned"); }
        String searchable() { return String.join(" ", serial, brand, model, os, assignedTo, condition); }
    }

    static class Inventory {
        final LinkedHashMap<String, Device> devices = new LinkedHashMap<>();
        final Path file;
        Inventory(Path file) throws IOException { this.file = file.toAbsolutePath(); load(); }
        static String key(String serial) { return serial.trim().toLowerCase(Locale.ROOT); }
        void add(Device device) {
            if (devices.containsKey(key(device.serial))) throw new IllegalArgumentException("Serial number already exists.");
            devices.put(key(device.serial), device);
        }
        Device get(String serial) { return devices.get(key(serial)); }
        boolean remove(String serial) { return devices.remove(key(serial)) != null; }
        List<Device> search(String query) {
            String q = query.toLowerCase(Locale.ROOT);
            List<Device> results = new ArrayList<>();
            for (Device d : devices.values())
                if (d.searchable().toLowerCase(Locale.ROOT).contains(q)) results.add(d);
            return results;
        }
        // Base64-encoded UTF-8 fields allow tabs, commas, and non-English characters.
        static String encode(String value) {
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
        static String decode(String value) {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        }
        void load() throws IOException {
            if (!Files.exists(file)) return;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals("inventory-v1"))
                throw new IOException("Unsupported inventory file. Original file was not changed.");
            try {
                for (int i = 1; i < lines.size(); i++) {
                    String[] f = lines.get(i).split("\t", -1);
                    if (f.length != 6) throw new IllegalArgumentException("Invalid field count.");
                    add(new Device(decode(f[0]), decode(f[1]), decode(f[2]), decode(f[3]), decode(f[4]), decode(f[5])));
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid inventory data. Original file was not changed.", error);
            }
        }
        void save() throws IOException {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>(); lines.add("inventory-v1");
            for (Device d : devices.values())
                lines.add(String.join("\t", encode(d.serial), encode(d.brand), encode(d.model),
                    encode(d.os), encode(d.assignedTo), encode(d.condition)));
            Path temp = Files.createTempFile(file.getParent(), "inventory-", ".tmp");
            try {
                Files.write(temp, lines, StandardCharsets.UTF_8);
                try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException error) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
        }
    }

    static String required(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Required fields cannot be blank.");
        return text.trim();
    }
    static String ask(Scanner scanner, String prompt) {
        System.out.print(prompt + ": "); return scanner.nextLine();
    }
    static String chooseCondition(Scanner scanner) {
        System.out.println("1. Good  2. Needs Repair  3. Retired");
        int n = Integer.parseInt(ask(scanner, "Condition"));
        if (n < 1 || n > 3) throw new IllegalArgumentException("Choose 1, 2, or 3.");
        return CONDITIONS.get(n - 1);
    }
    static void display(List<Device> devices) {
        if (devices.isEmpty()) System.out.println("No matching devices.");
        for (Device d : devices) {
            System.out.printf("%n%s | %s %s | %s%n", d.serial, d.brand, d.model, d.os);
            System.out.printf("Condition: %s | %s | User: %s%n", d.condition, d.availability(),
                d.assignedTo.isEmpty() ? "None" : d.assignedTo);
        }
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void selfTest() throws IOException {
        Path dir = Files.createTempDirectory("inventory-test-");
        Path file = dir.resolve("devices.tsv");
        try {
            Inventory inventory = new Inventory(file);
            inventory.add(new Device("DEMO-1", "Demo\tBrand", "Laptop, 14", "Linux", "Алекс", "Good"));
            inventory.add(new Device("DEMO-2", "Example", "Desktop", "Windows", "", "Needs Repair"));
            boolean duplicateRejected = false;
            try { inventory.add(new Device("demo-1", "A", "B", "C", "", "Good")); }
            catch (IllegalArgumentException expected) { duplicateRejected = true; }
            check(duplicateRejected, "Duplicate serial must be rejected");
            inventory.save();
            Inventory reloaded = new Inventory(file);
            check(reloaded.devices.size() == 2, "Reload count");
            check(reloaded.get("demo-1").brand.equals("Demo\tBrand"), "Special character round trip");
            check(reloaded.search("linux").size() == 1, "Case-insensitive search");
            check(reloaded.get("demo-2").availability().equals("Out of Service"), "Repair devices are unavailable");
            reloaded.get("demo-1").assignedTo = "";
            reloaded.get("demo-1").condition = "Retired";
            check(reloaded.remove("DEMO-2"), "Delete existing");
            check(!reloaded.remove("MISSING"), "Delete missing");
            reloaded.save();
            Inventory last = new Inventory(file);
            check(last.devices.size() == 1 && last.get("demo-1").condition.equals("Retired"), "Persist changes");
            boolean blankRejected = false;
            try { new Device(" ", "A", "B", "C", "", "Good"); }
            catch (IllegalArgumentException expected) { blankRejected = true; }
            check(blankRejected, "Blank serial");
            Files.writeString(file, "broken data");
            boolean corruptRejected = false;
            try { new Inventory(file); } catch (IOException expected) { corruptRejected = true; }
            check(corruptRejected && Files.readString(file).equals("broken data"), "Corrupt file preserved");
            System.out.println("All inventory self-tests passed.");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
    }
    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--self-test")) { selfTest(); return; }
            Path path = Path.of(args.length > 0 ? args[0] : "devices.tsv");
            Inventory inventory = new Inventory(path);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\nCOMPUTER INVENTORY MANAGER\n1. Add device\n2. List devices\n3. Search\n4. Assign or return\n5. Edit device\n6. Delete device\n7. Summary\n0. Exit");
                String option = ask(scanner, "Choose").trim();
                try {
                    switch (option) {
                        case "0": return;
                        case "1": {
                            Device d = new Device(ask(scanner, "Serial"), ask(scanner, "Brand"),
                                ask(scanner, "Model"), ask(scanner, "Operating system"), "", chooseCondition(scanner));
                            inventory.add(d); inventory.save(); System.out.println("Device saved."); break;
                        }
                        case "2": display(inventory.search("")); break;
                        case "3": display(inventory.search(ask(scanner, "Search"))); break;
                        case "4": case "5": case "6": {
                            String serial = ask(scanner, "Serial"); Device d = inventory.get(serial);
                            if (d == null) { System.out.println("Device not found."); break; }
                            if (option.equals("4")) {
                                String user = ask(scanner, "User (blank to return device)").trim();
                                if (!user.isEmpty() && !d.condition.equals("Good"))
                                    throw new IllegalArgumentException("Only devices in Good condition can be assigned.");
                                d.assignedTo = user;
                            }
                            else if (option.equals("5")) {
                                // Validate all edits before replacing the original object.
                                Device edited = new Device(d.serial, ask(scanner, "Brand"), ask(scanner, "Model"),
                                    ask(scanner, "Operating system"), d.assignedTo, chooseCondition(scanner));
                                inventory.devices.put(Inventory.key(serial), edited);
                            } else {
                                if (!ask(scanner, "Type DELETE to confirm").equals("DELETE")) break;
                                inventory.remove(serial);
                            }
                            inventory.save(); System.out.println("Change saved."); break;
                        }
                        case "7": {
                            long assigned = inventory.devices.values().stream().filter(d -> !d.assignedTo.isEmpty()).count();
                            System.out.printf("Total: %d | Assigned: %d | Available: %d%n", inventory.devices.size(),
                                assigned, inventory.devices.values().stream().filter(d -> d.availability().equals("Available")).count()); break;
                        }
                        default: System.out.println("Choose a listed menu number.");
                    }
                } catch (IllegalArgumentException error) { System.out.println("Invalid input: " + error.getMessage()); }
            }
        } catch (NoSuchElementException error) { System.out.println("\nGoodbye."); }
        catch (IOException error) {
            System.err.println("File error: " + error.getMessage() + " Back up the data file before retrying.");
            System.exit(1);
        }
    }
}
