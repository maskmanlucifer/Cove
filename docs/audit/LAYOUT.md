# Pixel 6a layout pass (branch `feat/layout`)

Checked on an emulator set to 1080x2400 at 420 dpi (411 dp), plus 360 dp, font scale 1.3 and 2.0.

| Item | Before | After |
|---|---|---|
| Journal photos | 96 dp thumbnails in a strip | Full width of the content column (24 dp margins), own aspect ratio, 24 dp corners, tall photos centre-cropped at 1.25 x width (`PhotoMath.kt`). Stored 2048 px image decoded at display width by `PhotoLoader` (ImageDecoder target size, EXIF applied, 2 parallel decodes, LRU of 1/6 of the memory class, 8-64 MB). Blurred thumbnail first, 150 ms fade (instant under reduce motion). Size comes from the file header (no migration). Missing file: "Photo not available offline" + Retry (via `MediaFetcher`); undecodable: "Couldn't open this photo" + Retry. Bitmaps are not recycled by hand: Compose may still draw an evicted one, so the GC frees them. |
| Photo viewer | none | Tap a photo: black full-screen dialog (`PhotoViewer`), pager swipe, pinch/double-tap zoom to 4x, pan, close/share/remove (remove closes the viewer; the editor shows Undo), Back closes, copies the activity's FLAG_SECURE. |
| Voice note row | Label wrapped to two lines on 411 dp | One row: 40 dp play circle, slim progress bar, fixed-width duration, 48 dp remove. "Loading..." replaces the bar. From font scale 1.6 the duration sits under the bar. |
| Brief controls | 61.5 x 76 dp oval play button, 42 dp cells, "Text" wrapped | 64 dp circle, equal-weight cells with 48 dp minimum, labels `maxLines = 1`. Header title may take 2 lines; the voice-problem actions flow onto new lines. |
| Profile photo | initial only | Avatar opens "Profile photo" sheet (gallery via Photo Picker, camera via the existing FileProvider, Remove with Undo), in-app circle cropper (drag/pinch, `cropRect` maths), stored as 512 px WebP at `filesDir/profile/avatar.webp` (re-encoded: no EXIF/GPS). Device-local: no schema, sync or backup change; it is not in "Back up now". |
| Name | tap opened a sheet that saved on dismiss | Save/Cancel, one line, 40 characters, whitespace collapsed; initial is the first letter/digit (emoji or symbol falls back to the person icon). |

Camera: the manifest declares no CAMERA permission, so `TakePicture` hands the work to the system camera app and no permission guide is needed; a missing camera app shows a message.

Sweep at 411/360 dp and 1.3x/2.0x: Journal editor, Me header, Brief, Alarms, Add expense. Nothing else wrapped or overlapped. Add expense: the category chip row scrolls sideways under a pinned "+ New" (existing design), so "Transport" is cut at the edge at 1.3x.

Scroll (journal editor, 6 large photos, 6 up/down swipes, emulator): 190 frames, median 17 ms, 90th 19 ms, 95th 20 ms, 99th 21 ms (emulator renders at 60 Hz in software).

Debug: `--ei journalPhotos 7` creates entry `debug-photos` with wide/square/tall/very tall/EXIF-rotated/24 MP/corrupt photos and a voice row; open with `--es route journal/debug-photos` after a restart.
