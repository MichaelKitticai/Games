import {
  Input, Output, Conversion, ALL_FORMATS, BlobSource, BufferTarget, Mp4OutputFormat, canEncodeVideo,
} from './vendor/mediabunny.min.mjs';

// Targets chosen to match what WhatsApp Status itself delivers, so its own
// re-encode has as little work to do as possible (no big downscale, no
// frame-rate conversion, no huge bitrate drop).
const PRESETS = {
  hd: {
    imageLongEdge: 2560,
    imageMaxBytes: 3.5e6,
    videoShortEdge: 1080,
    videoMaxBitrate: 6e6,
    videoMaxBytesPerPart: null,
  },
  sd: {
    imageLongEdge: 1600,
    imageMaxBytes: 1e6,
    videoShortEdge: 720,
    videoMaxBitrate: 3.5e6,
    videoMaxBytesPerPart: 16e6,
  },
};

const $ = (sel) => document.querySelector(sel);
const resultsEl = $('#results');
const cardTpl = $('#card-tpl');
const queue = [];
let running = false;

function settings() {
  const mode = document.querySelector('input[name="mode"]:checked').value;
  const fps = $('#fps').value;
  return {
    mode,
    ...PRESETS[mode],
    segLen: Number($('#segLen').value),
    sharpen: Number($('#sharpen').value),
    frameRate: fps === 'source' ? undefined : Number(fps),
  };
}

// ---------- UI ----------

function fmtBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 ** 2).toFixed(1)} MB`;
}

function fmtTime(s) {
  const m = Math.floor(s / 60);
  const sec = Math.round(s % 60);
  return `${m}:${String(sec).padStart(2, '0')}`;
}

function baseName(name) {
  return name.replace(/\.[^.]+$/, '') || 'status';
}

function createCard(file) {
  const node = cardTpl.content.firstElementChild.cloneNode(true);
  const url = URL.createObjectURL(file);
  const thumb = node.querySelector('.thumb');
  if (file.type.startsWith('video/')) {
    const v = document.createElement('video');
    v.src = url;
    v.muted = true;
    v.playsInline = true;
    v.preload = 'metadata';
    v.style.cssText = 'width:100%;height:100%;object-fit:cover;border-radius:8px';
    thumb.append(v);
  } else {
    thumb.style.backgroundImage = `url("${url}")`;
  }
  node.querySelector('.name').textContent = file.name;
  node.querySelector('.meta').textContent = fmtBytes(file.size);
  resultsEl.prepend(node);

  const status = node.querySelector('.status');
  const progress = node.querySelector('progress');
  return {
    node,
    setMeta: (t) => { node.querySelector('.meta').textContent = t; },
    setStatus(text, kind = '') {
      status.textContent = text;
      status.className = `status ${kind}`;
    },
    setProgress(p) {
      if (p == null) { progress.hidden = true; return; }
      progress.hidden = false;
      progress.value = p;
    },
    outs: node.querySelector('.outs'),
  };
}

function shareSupported(files) {
  return !!(navigator.canShare && navigator.canShare({ files }));
}

async function shareFiles(files) {
  try {
    await navigator.share({ files });
  } catch (e) {
    if (e.name !== 'AbortError') alert(`Couldn't share: ${e.message}`);
  }
}

function download(file) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(file);
  a.download = file.name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 60_000);
}

function addOutput(card, file, label) {
  const row = document.createElement('div');
  row.className = 'out';
  const l = document.createElement('span');
  l.className = 'label';
  l.textContent = label;
  row.append(l);
  if (shareSupported([file])) {
    const share = document.createElement('button');
    share.className = 'btn primary';
    share.textContent = 'Share';
    share.onclick = () => shareFiles([file]);
    row.append(share);
  }
  const save = document.createElement('button');
  save.className = 'btn';
  save.textContent = 'Save';
  save.onclick = () => download(file);
  row.append(save);
  card.outs.append(row);
}

function addShareAll(card, files) {
  if (files.length < 2) return;
  const row = document.createElement('div');
  row.className = 'out';
  if (shareSupported(files)) {
    const b = document.createElement('button');
    b.className = 'btn primary';
    b.textContent = `Share all ${files.length} parts`;
    b.onclick = () => shareFiles(files);
    row.append(b);
  }
  const s = document.createElement('button');
  s.className = 'btn';
  s.textContent = 'Save all';
  s.onclick = () => files.forEach((f, i) => setTimeout(() => download(f), i * 400));
  row.append(s);
  card.outs.prepend(row);
}

// ---------- Images ----------

async function decodeImage(file) {
  try {
    return await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    // Some browsers (older Safari) don't support the options bag; fall back to <img>.
    const img = new Image();
    img.src = URL.createObjectURL(file);
    await img.decode();
    return img;
  }
}

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

// Halve repeatedly before the final resize: a single big drawImage downscale
// aliases and looks soft/jaggy, which WhatsApp's encoder then amplifies.
function resize(src, srcW, srcH, w, h) {
  let cur = src;
  let cw = srcW;
  let ch = srcH;
  while (cw / 2 >= w && ch / 2 >= h) {
    const nw = Math.round(cw / 2);
    const nh = Math.round(ch / 2);
    const c = makeCanvas(nw, nh);
    const ctx = c.getContext('2d');
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(cur, 0, 0, nw, nh);
    cur = c;
    cw = nw;
    ch = nh;
  }
  const out = makeCanvas(w, h);
  const ctx = out.getContext('2d', { willReadFrequently: true });
  ctx.fillStyle = '#fff'; // flatten transparent PNGs onto white instead of black
  ctx.fillRect(0, 0, w, h);
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(cur, 0, 0, w, h);
  return out;
}

// Unsharp mask with a 3x3 Gaussian. A small threshold keeps flat areas (sky,
// skin) from getting grainy, since noise is what WhatsApp compresses worst.
function sharpen(canvas, amount) {
  if (!amount) return;
  const { width: w, height: h } = canvas;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const img = ctx.getImageData(0, 0, w, h);
  const d = img.data;
  const tmp = new Uint16Array(d.length);
  const blur = new Uint16Array(d.length);
  const row = w * 4;

  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * row + x * 4;
      const l = x > 0 ? i - 4 : i;
      const r = x < w - 1 ? i + 4 : i;
      for (let c = 0; c < 3; c++) tmp[i + c] = d[l + c] + 2 * d[i + c] + d[r + c];
    }
  }
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * row + x * 4;
      const u = y > 0 ? i - row : i;
      const b = y < h - 1 ? i + row : i;
      for (let c = 0; c < 3; c++) blur[i + c] = tmp[u + c] + 2 * tmp[i + c] + tmp[b + c];
    }
  }
  const threshold = 3;
  for (let i = 0; i < d.length; i += 4) {
    for (let c = 0; c < 3; c++) {
      const diff = d[i + c] - blur[i + c] / 16;
      if (diff > threshold || diff < -threshold) d[i + c] = d[i + c] + amount * diff;
    }
  }
  ctx.putImageData(img, 0, 0);
}

function toBlob(canvas, q) {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', q));
}

async function processImage(file, card, s) {
  card.setStatus('Decoding...');
  let bmp;
  try {
    bmp = await decodeImage(file);
  } catch {
    throw new Error("Your browser can't open this image format. If it's HEIC, set your camera to \"Most Compatible\" or share it as JPEG first.");
  }
  const sw = bmp.width || bmp.naturalWidth;
  const sh = bmp.height || bmp.naturalHeight;
  const scale = Math.min(1, s.imageLongEdge / Math.max(sw, sh));
  const w = Math.round(sw * scale);
  const h = Math.round(sh * scale);

  card.setStatus('Resizing and sharpening...');
  await new Promise((r) => setTimeout(r)); // let the status paint
  const canvas = resize(bmp, sw, sh, w, h);
  bmp.close?.();
  sharpen(canvas, s.sharpen);

  card.setStatus('Encoding...');
  let q = 0.92;
  let blob = await toBlob(canvas, q);
  while (blob.size > s.imageMaxBytes && q > 0.8) {
    q -= 0.03;
    blob = await toBlob(canvas, q);
  }

  const out = new File([blob], `${baseName(file.name)}_status.jpg`, { type: 'image/jpeg' });
  card.setMeta(`${sw}×${sh} · ${fmtBytes(file.size)}  →  ${w}×${h} · ${fmtBytes(out.size)}`);
  card.setStatus('Ready', 'ok');
  addOutput(card, out, `Photo · ${fmtBytes(out.size)}`);
}

// ---------- Videos ----------

const even = (n) => Math.max(2, Math.round(n / 2) * 2);

async function processVideo(file, card, s) {
  card.setStatus('Reading video...');
  const probe = new Input({ source: new BlobSource(file), formats: ALL_FORMATS });
  const track = await probe.getPrimaryVideoTrack();
  if (!track) throw new Error('No video track found in this file.');
  const duration = await probe.computeDuration();
  const sw = track.displayWidth;
  const sh = track.displayHeight;
  probe.dispose?.();

  const scale = Math.min(1, s.videoShortEdge / Math.min(sw, sh));
  const w = even(sw * scale);
  const h = even(sh * scale);

  // Split evenly (e.g. 70 s at 60 s max -> 2 x 35 s) rather than leaving a tiny tail.
  const parts = Math.max(1, Math.ceil(duration / s.segLen - 0.01));
  const partLen = duration / parts;

  let bitrate = s.videoMaxBitrate;
  if (s.videoMaxBytesPerPart) {
    const budget = (s.videoMaxBytesPerPart * 8 * 0.92) / partLen - 160e3;
    bitrate = Math.min(bitrate, Math.max(1.2e6, budget));
  }
  bitrate = Math.round(bitrate);

  if (!(await canEncodeVideo('avc', { width: w, height: h, bitrate }))) {
    throw new Error("This browser can't encode H.264 video. Try Chrome on Android or Safari on iPhone.");
  }

  card.setMeta(`${sw}×${sh} · ${fmtTime(duration)} · ${fmtBytes(file.size)}  →  ${w}×${h}` +
    (parts > 1 ? ` · ${parts} parts` : ''));

  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn ghost';
  cancelBtn.textContent = 'Cancel';
  card.outs.append(cancelBtn);
  let current = null;
  let cancelled = false;
  cancelBtn.onclick = () => { cancelled = true; current?.cancel(); };

  const outputs = [];
  let noAudio = false;
  try {
    for (let i = 0; i < parts; i++) {
      if (cancelled) break;
      const start = i * partLen;
      const end = i === parts - 1 ? duration : (i + 1) * partLen;
      card.setStatus(parts > 1 ? `Encoding part ${i + 1} of ${parts}...` : 'Encoding...');

      const input = new Input({ source: new BlobSource(file), formats: ALL_FORMATS });
      const output = new Output({
        format: new Mp4OutputFormat({ fastStart: 'in-memory' }),
        target: new BufferTarget(),
      });
      current = await Conversion.init({
        input,
        output,
        tracks: 'primary',
        trim: { start, end },
        showWarnings: false,
        video: {
          width: w,
          height: h,
          fit: 'contain',
          codec: 'avc',
          bitrate,
          frameRate: s.frameRate,
          keyFrameInterval: 2,
          forceTranscode: true,
          allowTransformationMetadata: false,
        },
        audio: { codec: 'aac' },
      });
      // isValid stays true if only audio survives, so check the video track explicitly.
      const droppedVideo = current.discardedTracks.find((t) => t.track.type === 'video');
      if (!current.isValid || droppedVideo) {
        const why = current.discardedTracks.map((t) => t.reason.replaceAll('_', ' ')).join(', ');
        throw new Error(`This browser can't convert this video (${why}). Try Chrome on Android or Safari on iPhone.`);
      }
      if (current.discardedTracks.some((t) => t.track.type === 'audio')) noAudio = true;

      current.onProgress = (p) => card.setProgress((i + p) / parts);
      await current.execute();
      input.dispose?.();

      const suffix = parts > 1 ? `_part${i + 1}` : '';
      const out = new File([output.target.buffer], `${baseName(file.name)}_status${suffix}.mp4`, { type: 'video/mp4' });
      outputs.push(out);
      addOutput(card, out, `${parts > 1 ? `Part ${i + 1} · ` : ''}${fmtTime(end - start)} · ${fmtBytes(out.size)}`);
    }
  } catch (e) {
    if (cancelled) {
      card.setProgress(null);
      card.setStatus('Cancelled', 'err');
      return;
    }
    throw e;
  } finally {
    cancelBtn.remove();
  }

  card.setProgress(null);
  addShareAll(card, outputs);
  card.setStatus(noAudio ? "Ready (sound couldn't be converted in this browser)" : 'Ready', noAudio ? 'err' : 'ok');
}

// ---------- Queue ----------

async function runQueue() {
  if (running) return;
  running = true;
  while (queue.length) {
    const { file, card } = queue.shift();
    const s = settings();
    try {
      if (file.type.startsWith('video/')) await processVideo(file, card, s);
      else if (file.type.startsWith('image/') || /\.(heic|heif)$/i.test(file.name)) await processImage(file, card, s);
      else throw new Error('Not a photo or video.');
    } catch (e) {
      console.error(e);
      card.setProgress(null);
      card.setStatus(e.message || String(e), 'err');
    }
  }
  running = false;
}

function addFiles(files) {
  for (const file of files) {
    const card = createCard(file);
    card.setStatus('Waiting...');
    queue.push({ file, card });
  }
  runQueue();
}

$('#files').addEventListener('change', (e) => {
  addFiles([...e.target.files]);
  e.target.value = '';
});

const drop = $('#drop');
drop.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('over'); });
drop.addEventListener('dragleave', () => drop.classList.remove('over'));
drop.addEventListener('drop', (e) => {
  e.preventDefault();
  drop.classList.remove('over');
  addFiles([...e.dataTransfer.files]);
});

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
