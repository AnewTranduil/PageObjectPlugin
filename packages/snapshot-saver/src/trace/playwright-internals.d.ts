/**
 * Type declarations for Playwright internal modules.
 * These have no .d.ts files since they are private APIs.
 */

declare module 'playwright-core/lib/utils/isomorphic/trace/traceLoader' {
  export interface TraceLoaderBackend {
    entryNames(): Promise<string[]>;
    hasEntry(entryName: string): Promise<boolean>;
    readText(entryName: string): Promise<string | undefined>;
    readBlob(entryName: string): Promise<Blob | undefined>;
    isLive(): boolean;
  }

  export interface ContextEntry {
    origin: string;
    startTime: number;
    wallTime: number;
    endTime: number;
    browserName: string;
    options: { viewport: { width: number; height: number } };
    pages: PageEntry[];
    resources: unknown[];
    actions: ActionEntry[];
    events: unknown[];
    errors: unknown[];
    stdio: unknown[];
    hasSource: boolean;
    contextId: string;
  }

  export interface PageEntry {
    pageId: string;
    screencastFrames: ScreencastFrame[];
  }

  export interface ScreencastFrame {
    sha1: string;
    timestamp: number;
    frameSwapWallTime?: number;
  }

  export interface ActionEntry {
    type: string;
    callId: string;
    startTime: number;
    endTime: number;
    title?: string;
    apiName?: string;
    class: string;
    method: string;
    params: Record<string, unknown>;
    wallTime: number;
    beforeSnapshot?: string;
    afterSnapshot?: string;
    inputSnapshot?: string;
    error?: string;
    result?: unknown;
    pageId: string;
    parentId?: string;
    log: Array<{ time: number; message: string }>;
  }

  export interface SnapshotStorage {
    snapshotByName(pageOrFrameId: string, snapshotName: string): SnapshotRendererInstance | undefined;
  }

  export interface SnapshotRendererInstance {
    snapshotName: string;
    render(): { html: string; pageId: string; frameId: string; index: number };
    viewport(): { width: number; height: number };
    closestScreenshot(): string | undefined;
  }

  export class TraceLoader {
    contextEntries: ContextEntry[];
    load(backend: TraceLoaderBackend, progressFn: (done: number, total: number) => void): Promise<void>;
    storage(): SnapshotStorage;
    resourceForSha1(sha1: string): Promise<Blob | undefined>;
  }
}
