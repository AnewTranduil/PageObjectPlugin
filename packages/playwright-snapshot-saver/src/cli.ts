import { extractSnapshots } from './extractor';

async function main() {
  const args = process.argv.slice(2);

  if (args[0] !== 'extract') {
    console.error('Usage: playwright-snapshot-saver extract --source <path-or-url> [options]');
    console.error('');
    console.error('Commands:');
    console.error('  extract    Extract snapshots from Playwright traces');
    process.exit(1);
  }

  const parsed = parseArgs(args.slice(1));

  if (!parsed.source) {
    console.error('Error: --source is required');
    console.error('');
    console.error('Usage: playwright-snapshot-saver extract --source <path-or-url> [options]');
    console.error('');
    console.error('Options:');
    console.error('  --source <path|url>   Report directory, trace ZIP, or hosted report URL');
    console.error('  --output <dir>        Output directory (default: .snapshots)');
    console.error('  --page <name>         Filter by page name');
    console.error('  --state <name>        Filter by state name');
    console.error('  --screenshot          Enable screenshot generation (off by default)');
    console.error('  --no-manifest         Skip manifest.json generation');
    process.exit(1);
  }

  try {
    const result = await extractSnapshots({
      source: parsed.source,
      outputDir: parsed.output ?? '.snapshots',
      screenshot: parsed.screenshot,
      manifest: parsed.manifest,
      filter: {
        page: parsed.page,
        state: parsed.state,
      },
    });

    if (result.snapshots.length === 0) {
      console.log('No snapshots extracted.');
    } else {
      console.log(`Extracted ${result.snapshots.length} snapshot(s):`);
      for (const snap of result.snapshots) {
        console.log(`  ${snap.page}/${snap.state} → ${snap.outputDir}`);
      }
    }
  } catch (err: any) {
    console.error(`Error: ${err.message}`);
    process.exit(1);
  }
}

function parseArgs(args: string[]): {
  source?: string;
  output?: string;
  page?: string;
  state?: string;
  screenshot: boolean;
  manifest: boolean;
} {
  const result: any = { screenshot: false, manifest: true };
  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--source': result.source = args[++i]; break;
      case '--output': result.output = args[++i]; break;
      case '--page': result.page = args[++i]; break;
      case '--state': result.state = args[++i]; break;
      case '--screenshot': result.screenshot = true; break;
      case '--no-screenshot': result.screenshot = false; break;
      case '--no-manifest': result.manifest = false; break;
      default:
        console.error(`Unknown option: ${args[i]}`);
        process.exit(1);
    }
  }
  return result;
}

main();
