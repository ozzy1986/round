import { defineConfig } from 'vitest/config';
import tsconfig from './tsconfig.json' with { type: 'json' };

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    setupFiles: ['tests/setup.ts'],
    include: ['tests/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary'],
      include: ['src/**/*.ts'],
      exclude: ['src/server.ts', '**/*.d.ts'],
      thresholds: {
        statements: 80,
        branches: 80,
        functions: 80,
        lines: 80,
      },
    },
  },
  resolve: {
    extensions: ['.ts'],
  },
  esbuild: {
    target: (tsconfig.compilerOptions as { target?: string }).target ?? 'ES2022',
  },
});
