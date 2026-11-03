import glob
from faster_whisper import WhisperModel
m = WhisperModel('large-v3', device='cpu', compute_type='int8')
for f in sorted(glob.glob('clips/*.wav')):
    segs, _ = m.transcribe(f, language='th', beam_size=5, word_timestamps=True)
    ws = [w for s in segs for w in s.words]
    print(f, ''.join(w.word for w in ws), '| first:', ' '.join('%.2f%s' % (w.start, w.word.strip()) for w in ws[:4]), flush=True)
open('out/done','w').write('ok')
