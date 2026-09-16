import { DeleteObjectCommand, GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import sharp from 'sharp';
import type { AppConfig } from '../config/env.js';
import { AppError } from '../utils/errors.js';

export interface StorageService {
  putImage(input: Buffer, objectKey: string): Promise<{ key: string; url: string }>;
  getImage(objectKey: string): Promise<{ body: Buffer; contentType: string }>;
  delete(objectKey: string): Promise<void>;
}

async function processImage(input: Buffer): Promise<Buffer> {
  return sharp(input)
    .rotate()
    .resize(1280, 720, { fit: 'inside', withoutEnlargement: true })
    .jpeg({ quality: 65 })
    .toBuffer();
}

function safeChildPath(root: string, objectKey: string): string {
  const target = resolve(root, objectKey);
  const fromRoot = relative(root, target);
  if (isAbsolute(fromRoot) || fromRoot.startsWith(`..${sep}`) || fromRoot === '..') {
    throw new Error('Unsafe storage object key');
  }
  return target;
}

export class LocalStorageService implements StorageService {
  private readonly root: string;
  private readonly baseUrl: string;

  constructor(private readonly config: AppConfig) {
    this.root = resolve(process.cwd(), config.LOCAL_UPLOAD_DIR);
    this.baseUrl = config.LOCAL_UPLOAD_BASE_URL || '/uploads';
  }

  async putImage(input: Buffer, objectKey: string) {
    try {
      const target = safeChildPath(this.root, objectKey);
      const temporary = `${target}.uploading-${process.pid}-${Date.now()}`;
      const body = await processImage(input);
      await mkdir(dirname(target), { recursive: true });
      await writeFile(temporary, body, { flag: 'wx' });
      await rename(temporary, target);
      return { key: objectKey, url: `${this.baseUrl.replace(/\/$/, '')}/${objectKey}` };
    } catch {
      throw new AppError('INTERNAL_SERVER_ERROR', 'Photo upload failed', 502, { code: 'PHOTO_UPLOAD_FAILED' });
    }
  }

  async delete(objectKey: string) {
    try {
      await rm(safeChildPath(this.root, objectKey), { force: true });
    } catch {
      // Cleanup is best-effort after a database conflict or replacement.
    }
  }

  async getImage(objectKey: string) {
    try {
      return { body: await readFile(safeChildPath(this.root, objectKey)), contentType: 'image/jpeg' };
    } catch {
      throw new AppError('NOT_FOUND', 'Photo not found', 404, { code: 'PHOTO_NOT_FOUND' });
    }
  }

  get directory() {
    return this.root;
  }
}

export class S3StorageService implements StorageService {
  private readonly client: S3Client;

  constructor(private readonly config: AppConfig) {
    if (!config.S3_BUCKET || !config.S3_ACCESS_KEY_ID || !config.S3_SECRET_ACCESS_KEY) {
      throw new Error('S3 storage configuration is incomplete');
    }
    this.client = new S3Client({
      region: config.S3_REGION,
      endpoint: config.S3_ENDPOINT || undefined,
      forcePathStyle: Boolean(config.S3_ENDPOINT),
      credentials: { accessKeyId: config.S3_ACCESS_KEY_ID, secretAccessKey: config.S3_SECRET_ACCESS_KEY }
    });
  }

  async putImage(input: Buffer, objectKey: string) {
    try {
      const body = await processImage(input);
      await this.client.send(new PutObjectCommand({ Bucket: this.config.S3_BUCKET, Key: objectKey, Body: body, ContentType: 'image/jpeg' }));
      return { key: objectKey, url: this.config.S3_PUBLIC_BASE_URL ? `${this.config.S3_PUBLIC_BASE_URL.replace(/\/$/, '')}/${objectKey}` : objectKey };
    } catch {
      throw new AppError('INTERNAL_SERVER_ERROR', 'Photo upload failed', 502, { code: 'PHOTO_UPLOAD_FAILED' });
    }
  }

  async delete(objectKey: string) {
    await this.client.send(new DeleteObjectCommand({ Bucket: this.config.S3_BUCKET, Key: objectKey }));
  }

  async getImage(objectKey: string) {
    try {
      const result = await this.client.send(new GetObjectCommand({ Bucket: this.config.S3_BUCKET, Key: objectKey }));
      if (!result.Body) throw new Error('Missing object body');
      return { body: Buffer.from(await result.Body.transformToByteArray()), contentType: result.ContentType || 'image/jpeg' };
    } catch {
      throw new AppError('NOT_FOUND', 'Photo not found', 404, { code: 'PHOTO_NOT_FOUND' });
    }
  }
}
