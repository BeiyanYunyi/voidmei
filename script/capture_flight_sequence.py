#!/usr/bin/env python3
"""Capture bounded /state + /indicators sequences for mock_8111 scenario replay.

This records sampled flight data, not video or atomic game frames. Other endpoints
are intentionally absent, so a replay cannot accidentally display a stale map.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import time
import urllib.request
import urllib.error

ENDPOINTS = ('/state', '/indicators')
MAX_RESPONSE = 1024 * 1024
MAX_OUTPUT = 64 * 1024 * 1024


def read_endpoint(url):
    try:
        with urllib.request.urlopen(url, timeout=1) as response:
            body = response.read(MAX_RESPONSE + 1)
        if len(body) > MAX_RESPONSE:
            raise ValueError('Response exceeds 1 MiB')
        text = body.decode('utf-8')
        try:
            return json.loads(text), None
        except ValueError:
            return text, 'Non-JSON response retained'
    except urllib.error.HTTPError as error:
        message = str(error)
        error.close()
        return {'valid': False}, message
    except Exception as error:
        # No previous value is carried forward on transport failure.
        return {'valid': False}, str(error)


def capture(output, port=8111, duration=30, interval_ms=100):
    if not (1 <= port <= 65535 and .1 <= duration <= 120 and 50 <= interval_ms <= 5000):
        raise ValueError('Port: 1–65535; duration: 0.1–120 s; interval: 50–5000 ms')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    snapshots = output / 'snapshots'
    scenarios = output / 'scenarios'
    snapshots.mkdir()
    scenarios.mkdir()
    starts, steps, errors = [], [], []
    written = 0
    started = time.monotonic()
    interrupted = False
    capacity_reached = False
    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            while time.monotonic() - started < duration:
                tick = time.monotonic()
                futures = {ep: executor.submit(read_endpoint, 'http://127.0.0.1:%d%s' % (port, ep)) for ep in ENDPOINTS}
                frame = {}
                frame_errors = {}
                for ep, future in futures.items():
                    frame[ep], error = future.result()
                    if error:
                        frame_errors[ep] = error
                data = json.dumps(frame, ensure_ascii=False).encode('utf-8')
                if written + len(data) > MAX_OUTPUT:
                    capacity_reached = True
                    break
                name = 'flight-%06d' % len(steps)
                (snapshots / (name + '.json')).write_bytes(data)
                written += len(data)
                starts.append((tick - started) * 1000)
                steps.append({'snapshot': name, 'duration_ms': interval_ms})
                if frame_errors:
                    errors.append({'frame': len(steps) - 1, 'endpoints': frame_errors})
                remaining = min(tick + interval_ms / 1000, started + duration) - time.monotonic()
                if remaining > 0:
                    time.sleep(remaining)
    except KeyboardInterrupt:
        interrupted = True
    finally:
        elapsed = (time.monotonic() - started) * 1000
        for index, step in enumerate(steps):
            end = starts[index + 1] if index + 1 < len(starts) else elapsed
            step['duration_ms'] = max(1, round(end - starts[index]))
        if steps:
            (scenarios / 'recording.json').write_text(json.dumps({
                'desc': 'Captured flight samples; transport failures become invalid data; map/messages not captured',
                'loop': False, 'steps': steps,
            }, indent=2), encoding='utf-8')
        (output / 'capture.json').write_text(json.dumps({
            'endpoints': ENDPOINTS, 'frames': len(steps), 'elapsed_ms': round(elapsed),
            'requested_interval_ms': interval_ms, 'sample_starts_ms': starts,
            'snapshot_bytes': written, 'interrupted': interrupted, 'capacity_reached': capacity_reached,
            'errors': errors, 'scope': 'Sampled flight responses; not atomic frames, transport timing, or video',
        }, indent=2), encoding='utf-8')
    if not steps:
        raise RuntimeError('No complete flight samples captured')
    return len(steps)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='New directory; existing paths are never overwritten')
    parser.add_argument('--source-port', type=int, default=8111)
    parser.add_argument('--duration', type=float, default=30, help='Seconds, at most 120')
    parser.add_argument('--interval-ms', type=int, default=100)
    args = parser.parse_args()
    frames = capture(args.output, args.source_port, args.duration, args.interval_ms)
    print('Captured %d frames in %s' % (frames, args.output))
    print('Replay with mock_8111.py serve --snapshots-dir <output>/snapshots --scenarios-dir <output>/scenarios --scenario recording')


if __name__ == '__main__':
    main()
