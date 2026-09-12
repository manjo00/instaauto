"""Render autoinsta's event log and publish history as one readable timeline.

Called by tools/diagnostics.ps1; not usually run by hand.

    python tools/render_diagnostics.py <dir-with-autoinsta.db> [hours]

The point is a single ordered account. The 2026-09-09 incident had to be reconstructed by
correlating post_history against dumpsys output by eye, which is exactly the work this
removes.
"""

import datetime
import os
import sqlite3
import sys

HOURS_DEFAULT = 168


def when(ms):
    if not ms:
        return "-"
    return datetime.datetime.fromtimestamp(ms / 1000).strftime("%a %d %b %H:%M:%S")


def duration(ms):
    """Human-readable gap, for 'how late was this alarm'."""
    if ms is None:
        return "?"
    secs = ms / 1000.0
    if abs(secs) < 90:
        return f"{secs:.0f}s"
    if abs(secs) < 5400:
        return f"{secs / 60:.0f}m"
    return f"{secs / 3600:.1f}h"


def main():
    directory = sys.argv[1] if len(sys.argv) > 1 else "."
    hours = int(sys.argv[2]) if len(sys.argv) > 2 else HOURS_DEFAULT
    cutoff = (datetime.datetime.now().timestamp() - hours * 3600) * 1000

    con = sqlite3.connect(os.path.join(directory, "autoinsta.db"))
    con.row_factory = sqlite3.Row

    # ── Queue state, because it explains most "why is nothing happening" ──
    print()
    print("=== Queue settings ===")
    row = con.execute("SELECT * FROM queue_settings LIMIT 1").fetchone()
    if row:
        k = dict(row)
        paused = "PAUSED - nothing will post" if k.get("paused") else "running"
        print(f"  state          : {paused}")
        print(f"  catch-up window: {k.get('catchUpWindowMinutes')} min")
        holder = k.get("publishingPostId")
        if holder:
            print(f"  publish lease  : held by post {holder} "
                  f"since {when(k.get('publishingSinceMillis'))}")
        else:
            print("  publish lease  : free")

    print()
    print("=== Slots ===")
    days = {1: "Mon", 2: "Tue", 3: "Wed", 4: "Thu", 5: "Fri", 6: "Sat", 7: "Sun"}
    for r in con.execute("SELECT * FROM posting_slots ORDER BY dayOfWeek, hourOfDay"):
        k = dict(r)
        flag = "" if k.get("enabled") else "  (disabled)"
        print(f"  {days.get(k['dayOfWeek'], '?')} "
              f"{k['hourOfDay']:02d}:{k['minute']:02d}{flag}")

    print()
    print("=== Queue ===")
    for r in con.execute(
        "SELECT id,status,timingMode,queuePosition,scheduledAt FROM scheduled_posts "
        "WHERE status = 'SCHEDULED' ORDER BY queuePosition IS NULL, queuePosition"
    ):
        pos = r["queuePosition"]
        pos = f"#{pos}" if pos is not None else "fixed"
        print(f"  post {r['id']:<3} {pos:<6} {r['timingMode']:<6} -> {when(r['scheduledAt'])}")

    # ── Publish attempts, with the detail that decides slot reuse ────────
    print()
    print("=== Publish attempts ===")
    rows = list(con.execute(
        "SELECT postId,status,scheduledAt,postedAt,failureKind,instagramMediaId,errorMessage "
        "FROM post_history WHERE postedAt >= ? ORDER BY postedAt ASC", (cutoff,)))
    if not rows:
        print("  nothing in this window")
    previous = None
    for r in rows:
        kind = f" [{r['failureKind']}]" if r["failureKind"] else ""
        detail = r["instagramMediaId"] or (r["errorMessage"] or "")[:44]
        line = (f"  {when(r['postedAt'])}  post {r['postId']:<3} {r['status']:<7}{kind} "
                f"slot={when(r['scheduledAt'])}  {detail}")
        print(line)
        # Two publishes close together into one slot is the 09-09 signature.
        if previous and r["scheduledAt"] == previous["scheduledAt"]:
            gap = r["postedAt"] - previous["postedAt"]
            print(f"        ^^ SAME SLOT as post {previous['postId']}, "
                  f"{duration(gap)} later")
        previous = r

    # ── The event log ────────────────────────────────────────────────────
    print()
    print(f"=== Event log (last {hours}h) ===")
    try:
        events = list(con.execute(
            "SELECT atMillis,category,event,postId,detail FROM app_events "
            "WHERE atMillis >= ? ORDER BY atMillis ASC", (cutoff,)))
    except sqlite3.OperationalError:
        print("  no app_events table - the build on the device predates the event log")
        return

    if not events:
        print("  empty. Either nothing happened, or this build predates the event log.")
    for e in events:
        post = f"post {e['postId']}" if e["postId"] else ""
        detail = f"  {e['detail']}" if e["detail"] else ""
        print(f"  {when(e['atMillis'])}  {e['category']:<8} {e['event']:<32} "
              f"{post:<8}{detail}")


if __name__ == "__main__":
    main()
