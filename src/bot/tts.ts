import textToSpeech from '@google-cloud/text-to-speech';
import { cacheGet, cacheSet, CacheKeys, CacheTTL } from '../cache.js';

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
 * Results are cached by text:languageCode (Redis when REDIS_URL set, else in-memory).
 */
export async function synthesize(text: string, languageCode: string = 'en-US'): Promise<Buffer | null> {
  const key = CacheKeys.tts(text, languageCode);
  const cached = await cacheGet(key);
  if (cached) return Buffer.from(cached, 'base64');

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
      await cacheSet(key, buf.toString('base64'), CacheTTL.TTS_TTL_SEC);
      return buf;
    }
    return null;
  } catch {
    return null;
  }
}
