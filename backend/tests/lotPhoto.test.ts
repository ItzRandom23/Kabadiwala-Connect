import sharp from 'sharp';
import { describe, expect, it, vi } from 'vitest';
import { LotService } from '../src/services/lotService.js';

describe('Kabadiwala lot multi-photo contract', () => {
  it('stores up to six angle photos with stable indexed references', async () => {
    const lot = {
      id: 'LOT-1',
      collectorId: 'collector-1',
      materialCategory: 'PLASTIC',
      condition: 'INTACT',
      status: 'CREATED',
      imageProvenance: 'GALLERY',
      photoPath: null
    } as any;
    const updatePhoto = vi.fn().mockResolvedValue({ count: 1 });
    const repo = {
      findOwned: vi.fn().mockResolvedValue(lot),
      updatePhoto
    } as any;
    const storedKeys: string[] = [];
    const storage = {
      putImage: vi.fn(async (_buffer: Buffer, key: string) => {
        storedKeys.push(key);
        return { key, url: `/private/${key}` };
      }),
      getImage: vi.fn(),
      delete: vi.fn().mockResolvedValue(undefined)
    } as any;
    const service = new LotService(repo, storage);
    const front = await sharp({ create: { width: 400, height: 300, channels: 3, background: 'red' } }).jpeg().toBuffer();
    const side = await sharp({ create: { width: 300, height: 400, channels: 3, background: 'blue' } }).png().toBuffer();

    await service.uploadPhoto('LOT-1', 'collector-1', [
      { buffer: front, mimetype: 'image/jpeg' },
      { buffer: side, mimetype: 'image/png' }
    ]);

    expect(storedKeys).toEqual([
      'lots/collector-1/LOT-1.jpg',
      'lots/collector-1/LOT-1-1.jpg'
    ]);
    expect(updatePhoto).toHaveBeenCalledWith(
      'LOT-1',
      'collector-1',
      'lots/collector-1/LOT-1.jpg',
      '/api/v1/lots/LOT-1/photo',
      'GALLERY',
      storedKeys
    );
  });
});
