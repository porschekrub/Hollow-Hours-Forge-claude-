import com.helperbuddy.gen.Gen;
import com.helperbuddy.gen.HouseGen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GenDump {
    static String fmt(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    static String sha(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (byte x : h) b.append(String.format("%02x", x));
        return b.toString();
    }

    static Gen.Opts parse(String kv) {
        Gen.Opts o = new Gen.Opts();
        for (String p : kv.split(";")) {
            String[] a = p.split("=", 2);
            String k = a[0], v = a[1];
            switch (k) {
                case "type" -> o.type = v;
                case "style" -> o.style = Integer.parseInt(v);
                case "size" -> o.size = Integer.parseInt(v);
                case "floors" -> o.floors = Integer.parseInt(v);
                case "wood" -> o.wood = Integer.parseInt(v);
                case "roof" -> o.roof = Integer.parseInt(v);
                case "mat" -> o.mat = Integer.parseInt(v);
                case "variant" -> o.variant = Integer.parseInt(v);
                case "model" -> o.model = Integer.parseInt(v);
                case "w" -> o.w = Integer.parseInt(v);
                case "d" -> o.d = Integer.parseInt(v);
                case "height" -> o.height = Integer.parseInt(v);
                case "light" -> o.light = Boolean.parseBoolean(v);
                case "furniture" -> o.furniture = Boolean.parseBoolean(v);
                case "porch" -> o.porch = Boolean.parseBoolean(v);
                case "chimney" -> o.chimney = Boolean.parseBoolean(v);
                case "pool" -> o.pool = Boolean.parseBoolean(v);
                case "floorsExact" -> o.floorsExact = Boolean.parseBoolean(v);
                default -> throw new IllegalArgumentException(k);
            }
        }
        return o;
    }

    static String line(Gen.Blk b) {
        String item = b.itemSet ? (b.item == null ? "null" : b.item) : "-";
        String dep = b.dep == null ? "-" : b.dep[0] + ":" + b.dep[1] + ":" + b.dep[2];
        return b.x + "|" + b.y + "|" + b.z + "|" + b.id + "|" + fmt(b.ph) + "|" + (b.st == null ? "-" : b.st.canon()) + "|" + item + "|" + dep + "|"
                + (b.opt != null && b.opt ? "1" : "-") + "|" + (b.tag == null ? "-" : b.tag);
    }

    public static void main(String[] args) throws Exception {
        List<String> opts = Files.readAllLines(Path.of("opts.txt"));
        List<String> out = new ArrayList<>();
        for (String kv : opts) {
            if (kv.isBlank()) continue;
            Gen.Plan p;
            try {
                p = HouseGen.generate(parse(kv));
            } catch (Exception e) {
                out.add("ERROR " + e + " " + kv);
                continue;
            }
            String cc = p.clearCells == null ? "-" : p.clearCells.stream().map(c -> c.x() + "," + c.y() + "," + c.z()).collect(Collectors.joining(";"));
            String header = String.join("|", String.valueOf(p.W), String.valueOf(p.D), String.valueOf(p.minX), String.valueOf(p.maxX), String.valueOf(p.minZ),
                    String.valueOf(p.maxZ), String.valueOf(p.maxY), String.valueOf(p.front), fmt(p.doorX), String.valueOf(p.clearArea.x0), String.valueOf(p.clearArea.x1),
                    String.valueOf(p.clearArea.z0), String.valueOf(p.clearArea.z1), p.fillBlock, sha(cc).substring(0, 10), p.underground ? "1" : "0", p.needsItems ? "1" : "0");
            String text = header + "\n" + p.blocks.stream().map(GenDump::line).collect(Collectors.joining("\n"));
            out.add(sha(text) + " " + p.blocks.size() + " " + kv);
            if (args.length > 0 && kv.equals(args[0])) Files.writeString(Path.of("/tmp/java_plan.txt"), text);
        }
        Files.write(Path.of("java.txt"), out);
        System.out.println("plans " + out.size());
    }
}
