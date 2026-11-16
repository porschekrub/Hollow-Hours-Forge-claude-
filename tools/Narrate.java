import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Narration in the helper's own voice: the same engine and model as inside the mod. args: modelDir outDir sid linesFile */
public class Narrate {
    static void wav(String f, float[] s, int rate) throws Exception {
        float peak = 1e-6f;
        for (float v : s) peak = Math.max(peak, Math.abs(v));
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            int n = s.length * 2;
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + n)); o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
            o.writeInt(Integer.reverseBytes(rate)); o.writeInt(Integer.reverseBytes(rate * 2)); o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data"); o.writeInt(Integer.reverseBytes(n));
            for (float v : s) o.writeShort(Short.reverseBytes((short) Math.round(Math.max(-1, Math.min(1, v / peak * 0.9f)) * 32767)));
        }
    }

    public static void main(String[] a) throws Exception {
        String dir = a[0], out = a[1];
        int sid = Integer.parseInt(a[2]);
        List<String> lines = Files.readAllLines(Path.of(a[3]));
        var kitten = OfflineTtsKittenModelConfig.builder().setModel(dir + "/model.int8.onnx").setVoices(dir + "/voices.bin").setTokens(dir + "/tokens.txt").setDataDir(dir + "/espeak-ng-data").build();
        OfflineTts tts = new OfflineTts(OfflineTtsConfig.builder().setModel(OfflineTtsModelConfig.builder().setKitten(kitten).setNumThreads(2).build()).build());
        System.out.println("speakers " + tts.getNumSpeakers());
        for (int s = 0; s < tts.getNumSpeakers(); s++) {
            GeneratedAudio g = tts.generate(lines.get(0), s, 1.0f);
            wav(out + "/pick_" + s + ".wav", g.getSamples(), g.getSampleRate());
        }
        for (int i = 0; i < lines.size(); i++) {
            GeneratedAudio g = tts.generate(lines.get(i), sid, 1.0f);
            wav(out + "/raw_" + i + ".wav", g.getSamples(), g.getSampleRate());
            System.out.println(i + " " + g.getSamples().length / (double) g.getSampleRate() + " " + lines.get(i));
        }
    }
}
