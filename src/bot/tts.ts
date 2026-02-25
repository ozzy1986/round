import textToSpeech from '@google-cloud/text-to-speech';

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
 */
export async function synthesize(text: string, languageCode: string = 'en-US'): Promise<Buffer | null> {
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
      return Buffer.isBuffer(content) ? content : Buffer.from(content);
    }
    return null;
  } catch {
    return null;
  }
}
