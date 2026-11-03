import json
from faster_whisper import WhisperModel
m = WhisperModel('large-v3', device='cpu', compute_type='int8')
segs, info = m.transcribe('audio.mp3', language='th', word_timestamps=True, vad_filter=False, beam_size=5)
out = []
for s in segs:
    w = [(x.start, x.end, x.word) for x in s.words]
    out.append({'start': s.start, 'end': s.end, 'text': s.text, 'words': w})
    print('%.2f-%.2f %s' % (s.start, s.end, s.text), flush=True)
json.dump(out, open('out/segs.json', 'w'), ensure_ascii=False, indent=1)
