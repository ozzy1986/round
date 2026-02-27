import textToSpeech from '@google-cloud/text-to-speech';

const MAX_CACHE_SIZE = 1000;
const bufferCache = new Map<string, Buffer>();
const cacheKeys: string[] = [];

function evictOne(): void {
  if (cacheKeys.length === 0) return;
  const key = cacheKeys.shift();
  if (key) bufferCache.delete(key);
}

let client: textToSpeech.TextToSpeechClient | null = null;

function getTTSClient(): textToSpeech.TextToSpeechClient | null {
  if (client) return client;
  try {
    client = new textToSpeech.TextToSpeechClient();
    return client;
  } catch {
    return null;
  }
}

/**
 * Synthesizes speech for the given text. Returns buffer (OGG) or null if TTS unavailable.
 * Results are cached by text:languageCode (LRU, max 1000 entries).
 */
export async function synthesize(text: string, languageCode: string = 'en-US'): Promise<Buffer | null> {
  const key = `${text}:${languageCode}`;
  const cached = bufferCache.get(key);
  if (cached) return cached;

  const c = getTTSClient();
  if (!c) return null;
  try {
    const [response] = await c.synthesizeSpeech({
      input: { text },
      voice: {
        languageCode: languageCode.startsWith('ru') ? 'ru-RU' : 'en-US',
        name: languageCode.startsWith('ru') ? 'ru-RU-Standard-A' : 'en-US-Standard-C',
      },
      audioConfig: {
        audioEncoding: 'OGG_OPUS',
        sampleRateHertz: 24000,
      },
    });
    if (response.audioContent && typeof response.audioContent === 'object') {
      const content = response.audioContent as Uint8Array | Buffer;
      const buf = Buffer.isBuffer(content) ? content : Buffer.from(content);
      if (bufferCache.size >= MAX_CACHE_SIZE) evictOne();
      bufferCache.set(key, buf);
      cacheKeys.push(key);
      return buf;
    }
    return null;
  } catch {
    return null;
  }
}
