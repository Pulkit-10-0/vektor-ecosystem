import { puter } from '@heyputer/puter.js';

async function runOpus47Demo() {
  try {
    const response = await puter.ai.chat(
      'Write a short poem about coding.',
      { model: 'claude-opus-4-7' }
    );

    const text = response?.message?.content?.[0]?.text ?? JSON.stringify(response, null, 2);
    console.log(text);
  } catch (error) {
    console.error('Failed to call Claude Opus 4.7 via Puter.js:', error);
  }
}

runOpus47Demo();
