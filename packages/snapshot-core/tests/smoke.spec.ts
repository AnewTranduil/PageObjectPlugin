import { describe, it, expect } from 'vitest';

describe('snapshot-core smoke', () => {
  it('imports the empty barrel without error', async () => {
    const mod = await import('../src/index');
    expect(mod).toBeTypeOf('object');
  });
});
