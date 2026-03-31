import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
  TestResult,
  TestStep,
} from '@playwright/test/reporter';
import * as path from 'path';
import { extractSnapshots } from './extractor';

interface SnapshotReporterOptions {
  outputDir?: string;
  screenshot?: boolean;
  manifest?: boolean;
}

interface CollectedMarker {
  page: string;
  state: string;
  testTitle: string;
  tracePath?: string;
}

const MARKER_REGEX = /^\[snapshot:([a-zA-Z0-9_-]+)\/([a-zA-Z0-9_-]+)\]$/;

class SnapshotReporter implements Reporter {
  private options: SnapshotReporterOptions;
  private markers: CollectedMarker[] = [];
  private tracingEnabled = false;

  constructor(options: SnapshotReporterOptions = {}) {
    this.options = options;
  }

  onBegin(config: FullConfig, suite: Suite): void {
    for (const project of config.projects) {
      const trace = project.use?.trace;
      if (trace && trace !== 'off') {
        this.tracingEnabled = true;
        break;
      }
    }
    if (!this.tracingEnabled) {
      console.warn(
        '[snapshot-reporter] Warning: Tracing is not enabled. ' +
        'Set trace: "on" in playwright.config.ts to enable snapshot extraction.'
      );
    }
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    if (!this.tracingEnabled) return;

    const traceAttachment = result.attachments.find(a => a.name === 'trace');
    const tracePath = traceAttachment?.path;

    const scanSteps = (steps: TestStep[]) => {
      for (const step of steps) {
        const match = MARKER_REGEX.exec(step.title);
        if (match) {
          this.markers.push({
            page: match[1],
            state: match[2],
            testTitle: test.title,
            tracePath,
          });
        }
        if (step.steps) scanSteps(step.steps);
      }
    };
    scanSteps(result.steps);
  }

  async onEnd(result: FullResult): Promise<void> {
    if (this.markers.length === 0) return;

    const outputDir = this.options.outputDir ?? '.snapshots';

    // Group markers by trace file, extract per trace
    const byTrace = new Map<string, CollectedMarker[]>();
    for (const marker of this.markers) {
      if (!marker.tracePath) {
        console.warn(
          `[snapshot-reporter] Snapshot marker '${marker.page}/${marker.state}' ` +
          `skipped — no trace file for test "${marker.testTitle}"`
        );
        continue;
      }
      const existing = byTrace.get(marker.tracePath) ?? [];
      existing.push(marker);
      byTrace.set(marker.tracePath, existing);
    }

    for (const [tracePath] of byTrace) {
      try {
        await extractSnapshots({
          source: tracePath,
          outputDir,
          screenshot: this.options.screenshot,
          manifest: this.options.manifest,
        });
      } catch (err) {
        console.error(`[snapshot-reporter] Failed to extract from ${tracePath}:`, err);
      }
    }

    console.log(`[snapshot-reporter] Extracted ${this.markers.length} snapshot(s) to ${outputDir}`);
  }
}

export default SnapshotReporter;
