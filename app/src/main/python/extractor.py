import json
import os
import re
import yt_dlp
from yt_dlp.utils import DownloadError, ExtractorError

# Active cancellation registry to instantly abort downloads on pause or cancel
_cancelled_tasks = set()

def cancel_task(task_id: str):
    """Mark a task as cancelled or paused to immediately terminate yt-dlp execution."""
    if task_id:
        _cancelled_tasks.add(str(task_id))

def reset_task_cancellation(task_id: str):
    """Reset cancellation state when starting or resuming a task."""
    if task_id:
        _cancelled_tasks.discard(str(task_id))

def is_task_cancelled(task_id: str) -> bool:
    return str(task_id) in _cancelled_tasks

def sanitize_url(raw_url: str) -> str:
    """Extract and sanitize URL from input text, fixing accidental double slashes."""
    if not raw_url:
        return ""
    match = re.search(r'https?://[^\s]+', raw_url)
    clean = match.group(0) if match else raw_url.strip()
    clean = re.sub(r'(?<!:)//+', '/', clean)
    return clean.strip()

def normalize_youtube_shorts(url: str) -> str:
    """Convert youtube.com/shorts/ID to standard watch?v=ID as fallback."""
    m = re.search(r'youtube\.com/shorts/([A-Za-z0-9_-]{6,})', url)
    if m:
        return f"https://www.youtube.com/watch?v={m.group(1)}"
    return url

def is_youtube_url(url: str) -> bool:
    low = (url or "").lower()
    return "youtube.com" in low or "youtu.be" in low

def format_filesize(size_bytes):
    """Format bytes into human-readable string (KB, MB, GB)."""
    if not size_bytes or size_bytes <= 0:
        return "Unknown size"
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.1f} PB"

def format_duration(seconds):
    """Format duration in seconds to HH:MM:SS or MM:SS."""
    if not seconds:
        return "Unknown"
    try:
        seconds = int(seconds)
        hours = seconds // 3600
        minutes = (seconds % 3600) // 60
        secs = seconds % 60
        if hours > 0:
            return f"{hours:02d}:{minutes:02d}:{secs:02d}"
        return f"{minutes:02d}:{secs:02d}"
    except Exception:
        return "Unknown"

def format_eta(seconds):
    """Format ETA seconds to readable string."""
    if not seconds or seconds <= 0:
        return ""
    try:
        seconds = int(seconds)
        if seconds < 60:
            return f"{seconds}s left"
        minutes = seconds // 60
        rem_sec = seconds % 60
        if minutes < 60:
            return f"{minutes}m {rem_sec}s left"
        hours = minutes // 60
        rem_min = minutes % 60
        return f"{hours}h {rem_min}m left"
    except Exception:
        return ""

def estimate_video_bytes(height, duration_seconds):
    """Fallback calculator for video file size based on resolution and duration."""
    d = duration_seconds if (duration_seconds and duration_seconds > 0) else 180
    h = height or 720
    if h >= 2160:
        kbps = 7500
    elif h >= 1440:
        kbps = 4500
    elif h >= 1080:
        kbps = 2500
    elif h >= 720:
        kbps = 1400
    elif h >= 480:
        kbps = 700
    elif h >= 360:
        kbps = 400
    else:
        kbps = 220
    return int((kbps * 1000 / 8) * d)

def classify_error(raw_error: str):
    """
    Maps any technical download/extraction exception to a clean (error_code, friendly_message)
    tuple so users never see raw HTTP codes, stack traces, or technical jargon.
    """
    err_str = str(raw_error or "")
    low = err_str.lower()

    if any(k in low for k in [
        "errno 7", "no address associated", "network is unreachable",
        "unable to download api page", "nodename nor servname",
        "connection refused", "failed to resolve", "name or service not known",
        "connection reset", "connection aborted", "remote end closed"
    ]):
        return ("network", "Could not connect to the internet. Please check your connection and try again.")

    if any(k in low for k in ["timed out", "timeout", "read timed out"]):
        return ("timeout", "The connection timed out. Please check your network speed and try again.")

    if any(k in low for k in ["416", "range not satisfiable"]):
        return ("http_416", "The download session expired or became out of sync. Please tap Retry to start cleanly.")

    if any(k in low for k in ["403", "forbidden", "unable to download video data"]):
        return ("forbidden", "Access to this video link expired or was declined by the server. Please refresh the link and try again.")

    if any(k in low for k in ["429", "too many requests", "rate limit"]):
        return ("rate_limit", "Too many requests were sent in a short time. Please wait a moment and try again.")

    if any(k in low for k in ["not a bot", "sign in to confirm", "cookies", "logged-in", "login required", "confirm your age", "age-restricted"]):
        return ("bot_check", "This platform temporarily asks for verification or sign-in on this video. Please try another link or try again later.")

    if any(k in low for k in ["private video", "this video is private", "members-only", "subscribers only"]):
        return ("private", "This video is private or restricted and cannot be saved.")

    if any(k in low for k in ["video unavailable", "has been removed", "been deleted", "404", "not found", "does not exist"]):
        return ("unavailable", "This video is unavailable or has been removed from the platform.")

    if any(k in low for k in ["geo", "not available in your country", "blocked in your country"]):
        return ("geo_blocked", "This video is restricted in your current region.")

    if "unsupported url" in low or "is not a valid url" in low:
        return ("unsupported_url", "This link format is not supported. Please paste a direct public video link.")

    if "requested format is not available" in low:
        return ("format_unavailable", "The selected quality is no longer available for this video. Please choose another quality.")

    return ("general", "Could not process this video right now. Please check the link or try again in a moment.")

def humanize_error(raw_error: str) -> str:
    _, msg = classify_error(raw_error)
    return msg

def _build_base_headers():
    return {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Accept': '*/*',
        'Accept-Language': 'en-US,en;q=0.9,ar;q=0.8',
        'Referer': 'https://www.google.com/',
    }

def _remove_partial_files(paths):
    """Delete stale/broken .part or temp files when recovering from HTTP 416 range mismatch."""
    for p in paths:
        if not p:
            continue
        for candidate in [p, f"{p}.part", f"{p}.ytdl"]:
            try:
                if os.path.exists(candidate):
                    os.remove(candidate)
            except Exception:
                pass

def extract_media_info(url: str) -> str:
    """
    Extracts all available video qualities with optimized format selection:
    - Detects playlists cleanly and returns batch items
    - Normalizes vertical video resolution (e.g. 720x1280 -> 720p HD)
    - Keeps single progressive streams intact when audio is already embedded
    - Uses format 18 for YouTube 360p (~49MB) to avoid bloat
    - Falls back across YouTube player clients and Shorts -> Watch URL if needed
    """
    clean_url = sanitize_url(url)
    if not clean_url:
        return json.dumps({
            "status": "error",
            "error_code": "unsupported_url",
            "message": "URL cannot be empty."
        })

    base_opts = {
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extract_flat': 'in_playlist',
        'socket_timeout': 30,
        'nocheckcertificate': True,
        'http_headers': _build_base_headers(),
    }

    info = None
    last_error = None

    # Build candidate extraction strategies (especially resilient for YouTube Shorts & mobile IPs)
    attempts = [(clean_url, dict(base_opts))]
    if is_youtube_url(clean_url):
        alt_url = normalize_youtube_shorts(clean_url)
        for clients in [['tv', 'mweb'], ['tv_simply', 'visionos'], ['android_vr', 'ios']]:
            o = dict(base_opts)
            o['extractor_args'] = {'youtube': {'player_client': clients}}
            attempts.append((clean_url, o))
        if alt_url != clean_url:
            attempts.append((alt_url, dict(base_opts)))
            o2 = dict(base_opts)
            o2['extractor_args'] = {'youtube': {'player_client': ['tv', 'mweb']}}
            attempts.append((alt_url, o2))

    for target_url, opts in attempts:
        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                extracted = ydl.extract_info(target_url, download=False)
                if extracted:
                    # Ensure either playlist entries or playable formats exist
                    is_pl = extracted.get('_type') == 'playlist' or ('entries' in extracted and len(extracted.get('entries') or []) > 1)
                    if is_pl or extracted.get('formats') or extracted.get('url'):
                        info = extracted
                        break
        except Exception as e:
            last_error = e
            err_code, _ = classify_error(str(e))
            # Don't waste time retrying if there is literally no internet or video is definitely deleted
            if err_code in ("network", "private", "unavailable", "unsupported_url"):
                break

    if not info:
        if last_error:
            err_code, err_msg = classify_error(str(last_error))
            return json.dumps({
                "status": "error",
                "error_code": err_code,
                "message": err_msg
            })
        return json.dumps({
            "status": "error",
            "error_code": "general",
            "message": "Could not inspect video. Please verify the link is public."
        })

    try:
        # Check if this is a Playlist / Album
        if info.get('_type') == 'playlist' or ('entries' in info and len(info.get('entries') or []) > 1):
            entries = info.get('entries') or []
            playlist_items = []
            for idx, entry in enumerate(entries[:50]):
                if not entry:
                    continue
                item_url = entry.get('url') or entry.get('webpage_url') or ""
                if not item_url and entry.get('id'):
                    item_url = f"https://www.youtube.com/watch?v={entry.get('id')}"
                playlist_items.append({
                    "id": str(entry.get('id') or idx),
                    "title": entry.get('title') or f"Video #{idx+1}",
                    "url": item_url,
                    "duration": format_duration(entry.get('duration')),
                    "thumbnail": entry.get('thumbnail') or ""
                })

            return json.dumps({
                "status": "success",
                "is_playlist": True,
                "title": info.get('title') or "Media Playlist",
                "uploader": info.get('uploader') or info.get('channel') or "Playlist",
                "duration": f"{len(playlist_items)} items",
                "duration_seconds": 0,
                "thumbnail": playlist_items[0]["thumbnail"] if playlist_items else "",
                "webpage_url": clean_url,
                "playlist_items": playlist_items,
                "formats": [],
                "audio_tracks": []
            })

        if 'entries' in info and info['entries']:
            info = info['entries'][0]

        title = info.get('title') or info.get('description') or "Untitled Video"
        if len(title) > 90:
            title = title[:90] + "..."
        duration = info.get('duration') or 0
        uploader = info.get('uploader') or info.get('channel') or info.get('creator') or "Source"
        thumbnail = info.get('thumbnail') or ""
        webpage_url = info.get('webpage_url') or clean_url

        raw_formats = info.get('formats') or []
        if not raw_formats and info.get('url'):
            raw_formats = [{
                'format_id': str(info.get('format_id') or 'best'),
                'ext': info.get('ext') or 'mp4',
                'vcodec': info.get('vcodec') or 'h264',
                'acodec': info.get('acodec') or 'aac',
                'height': info.get('height') or 720,
                'width': info.get('width') or 1280,
                'filesize': info.get('filesize') or 0,
                'url': info.get('url')
            }]

        # 1. Identify Multi-language Audio Tracks (Default to Original)
        audio_track_map = {}
        audio_only_formats = []

        for fmt in raw_formats:
            acodec = fmt.get('acodec') or 'none'
            vcodec = fmt.get('vcodec') or 'none'
            if acodec != 'none' and vcodec == 'none':
                fmt_id = str(fmt.get('format_id', ''))
                lang = (fmt.get('language') or 'default').lower()
                note = (fmt.get('format_note') or '').lower()
                is_orig = 'orig' in note or 'orig' in fmt_id.lower() or fmt.get('is_default') == True

                abr = fmt.get('abr') or 128
                ext = (fmt.get('ext') or 'm4a').lower()

                display_lang = fmt.get('language_preference') or fmt.get('language') or "Default"
                if is_orig:
                    display_name = f"{display_lang.capitalize()} (Original)"
                elif note:
                    display_name = f"{display_lang.capitalize()} ({note.capitalize()})"
                else:
                    display_name = display_lang.capitalize()

                if lang not in audio_track_map or is_orig:
                    audio_track_map[lang] = {
                        "id": fmt_id,
                        "language": lang,
                        "display_name": display_name,
                        "is_original": is_orig,
                        "bitrate": int(abr)
                    }

                sz = fmt.get('filesize') or fmt.get('filesize_approx') or 0
                if sz <= 0 and duration > 0:
                    sz = int((abr * 1000 / 8) * duration)

                audio_only_formats.append({
                    "format_id": fmt_id,
                    "resolution": f"Audio ({int(abr)} kbps)",
                    "ext": ext if ext in ['m4a', 'mp3', 'aac'] else 'm4a',
                    "filesize_bytes": sz,
                    "filesize_formatted": format_filesize(sz),
                    "stream_type": "Audio Only",
                    "has_audio": True,
                    "has_video": False,
                    "fps": 0,
                    "format_note": display_name,
                    "stream_url": fmt.get('url') or "",
                    "quality_score": int(abr),
                    "height": 0
                })

        audio_tracks = list(audio_track_map.values())
        audio_tracks.sort(key=lambda t: (not t["is_original"], t["language"]))

        # Primary original audio stream to pair with video-only DASH formats
        best_audio_fmt = None
        m4a_audio = [
            f for f in raw_formats
            if (f.get('acodec') or 'none') != 'none'
            and (f.get('vcodec') or 'none') == 'none'
            and (f.get('ext') == 'm4a' or 'mp4a' in (f.get('acodec') or ''))
        ]
        orig_m4a = [
            f for f in m4a_audio
            if 'orig' in (f.get('format_note') or '').lower()
            or 'orig' in str(f.get('format_id')).lower()
            or f.get('is_default')
        ]
        if orig_m4a:
            best_audio_fmt = orig_m4a[0]
        elif m4a_audio:
            fmt_140 = next((f for f in m4a_audio if str(f.get('format_id')) == '140'), None)
            best_audio_fmt = fmt_140 if fmt_140 else max(m4a_audio, key=lambda f: f.get('abr') or 0)
        else:
            any_audio = [
                f for f in raw_formats
                if (f.get('acodec') or 'none') != 'none' and (f.get('vcodec') or 'none') == 'none'
            ]
            best_audio_fmt = any_audio[0] if any_audio else None

        best_audio_id = str(best_audio_fmt.get('format_id', '')) if best_audio_fmt else ''
        best_audio_size = (best_audio_fmt.get('filesize') or best_audio_fmt.get('filesize_approx') or 0) if best_audio_fmt else 0
        if best_audio_fmt and best_audio_size <= 0 and duration > 0:
            best_audio_size = int((128 * 1000 / 8) * duration)

        # 2. Group Video Resolutions using true shortest-side resolution (handles vertical Shorts/TikTok cleanly)
        video_resolutions = {}
        for fmt in raw_formats:
            vcodec = fmt.get('vcodec') or 'none'
            if vcodec == 'none':
                continue

            w = fmt.get('width') or 0
            h = fmt.get('height') or 0
            if w > 0 and h > 0:
                eff_h = min(w, h)
            elif h > 0:
                eff_h = h
            elif w > 0:
                eff_h = int(w * 9 / 16)
            else:
                eff_h = 720

            if eff_h not in video_resolutions:
                video_resolutions[eff_h] = []
            video_resolutions[eff_h].append(fmt)

        sorted_heights = sorted(video_resolutions.keys(), reverse=True)
        complete_video_formats = []

        for h in sorted_heights:
            fmts_for_h = video_resolutions[h]

            # Progressive formats ALREADY contain BOTH video + audio
            prog_fmts = [f for f in fmts_for_h if (f.get('acodec') or 'none') != 'none']
            v_only_fmts = [f for f in fmts_for_h if (f.get('acodec') or 'none') == 'none']

            # Pick best compatible progressive MP4 (prefer h264/avc over bytevc1/hevc for universal Android playback)
            prog_mp4_h264 = [
                f for f in prog_fmts
                if (f.get('ext') == 'mp4' or 'mp4' in (f.get('container') or ''))
                and ('avc' in (f.get('vcodec') or '').lower() or 'h264' in (f.get('vcodec') or '').lower())
            ]
            prog_mp4 = [
                f for f in prog_fmts
                if (f.get('ext') == 'mp4' or 'mp4' in (f.get('container') or ''))
            ]
            prog_fmt = (
                max(prog_mp4_h264, key=lambda f: f.get('tbr') or 0) if prog_mp4_h264
                else max(prog_mp4, key=lambda f: f.get('tbr') or 0) if prog_mp4
                else (prog_fmts[0] if prog_fmts else None)
            )

            res_title = (
                f"{h}p 4K UHD" if h >= 2160 else
                f"{h}p 2K QHD" if h >= 1440 else
                f"{h}p Full HD" if h >= 1080 else
                f"{h}p HD" if h >= 720 else
                f"{h}p"
            )

            # Use single progressive format directly whenever available for <=720p OR when no DASH video-only exists
            if prog_fmt and (h <= 720 or not v_only_fmts or not best_audio_id):
                raw_sz = prog_fmt.get('filesize') or prog_fmt.get('filesize_approx') or 0
                if raw_sz <= 0 and duration > 0:
                    tbr = prog_fmt.get('tbr')
                    raw_sz = int((tbr * 1000 / 8) * duration) if tbr and tbr > 0 else estimate_video_bytes(h, duration)

                complete_video_formats.append({
                    "format_id": str(prog_fmt.get('format_id')),
                    "resolution": res_title,
                    "ext": "mp4",
                    "filesize_bytes": raw_sz,
                    "filesize_formatted": format_filesize(raw_sz),
                    "stream_type": "Video + Audio",
                    "has_audio": True,
                    "has_video": True,
                    "fps": prog_fmt.get('fps') or 30,
                    "format_note": "Direct MP4 (Optimized)",
                    "stream_url": prog_fmt.get('url') or "",
                    "quality_score": h * 10 + 1000,
                    "height": h
                })
            elif v_only_fmts and best_audio_id:
                mp4_v_fmts = [
                    f for f in v_only_fmts
                    if (f.get('ext') == 'mp4' or 'avc' in (f.get('vcodec') or '').lower())
                ]
                best_v_fmt = mp4_v_fmts[0] if mp4_v_fmts else v_only_fmts[0]

                v_size = best_v_fmt.get('filesize') or best_v_fmt.get('filesize_approx') or 0
                if v_size <= 0 and duration > 0:
                    vbr = best_v_fmt.get('vbr') or best_v_fmt.get('tbr')
                    v_size = int((vbr * 1000 / 8) * duration) if vbr and vbr > 0 else estimate_video_bytes(h, duration)

                combined_filesize = v_size + best_audio_size
                v_fmt_id = str(best_v_fmt.get('format_id'))
                composite_id = f"{v_fmt_id}+{best_audio_id}"

                complete_video_formats.append({
                    "format_id": composite_id,
                    "resolution": res_title,
                    "ext": "mp4",
                    "filesize_bytes": combined_filesize,
                    "filesize_formatted": format_filesize(combined_filesize),
                    "stream_type": "Video + Audio (Merged)",
                    "has_audio": True,
                    "has_video": True,
                    "fps": best_v_fmt.get('fps') or 30,
                    "format_note": f"Video: {format_filesize(v_size)} + Audio: {format_filesize(best_audio_size)}",
                    "stream_url": best_v_fmt.get('url') or "",
                    "quality_score": h * 10,
                    "height": h
                })

        unique_audio = []
        seen_res = set()
        audio_only_formats.sort(key=lambda x: x["quality_score"], reverse=True)
        for af in audio_only_formats:
            if af["resolution"] not in seen_res:
                seen_res.add(af["resolution"])
                unique_audio.append(af)

        all_formats = complete_video_formats + unique_audio

        return json.dumps({
            "status": "success",
            "is_playlist": False,
            "title": title,
            "duration": format_duration(duration),
            "duration_seconds": duration,
            "uploader": uploader,
            "thumbnail": thumbnail,
            "webpage_url": webpage_url,
            "formats": all_formats,
            "audio_tracks": audio_tracks,
            "playlist_items": []
        })

    except Exception as e:
        err_code, err_msg = classify_error(str(e))
        return json.dumps({"status": "error", "error_code": err_code, "message": err_msg})

def _execute_ydl_with_recovery(ydl_opts, target_url, temp_paths, fallback_formats=None):
    """
    Runs yt-dlp download with automatic recovery from:
    1) HTTP 416 Requested Range Not Satisfiable (deletes corrupt/out-of-sync .part files & disables chunking/range)
    2) Requested format is not available (steps down cleanly through fallback formats)
    """
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([target_url])
            return
    except Exception as err:
        err_str = str(err)
        if "TASK_CANCELLED" in err_str:
            raise err

        low = err_str.lower()
        # Recover cleanly from HTTP 416 / Range mismatch (common on TikTok/CDNs with .part or chunking)
        if "416" in low or "range not satisfiable" in low:
            _remove_partial_files(temp_paths)
            retry_opts = dict(ydl_opts)
            retry_opts.pop('http_chunk_size', None)
            retry_opts['continuedl'] = False
            retry_opts['nopart'] = True
            with yt_dlp.YoutubeDL(retry_opts) as ydl:
                ydl.download([target_url])
            return

        # Recover cleanly if selected format token expired or isn't served
        if ("requested format is not available" in low or "no video formats" in low) and fallback_formats:
            for fb_fmt in fallback_formats:
                try:
                    retry_opts = dict(ydl_opts)
                    retry_opts['format'] = fb_fmt
                    with yt_dlp.YoutubeDL(retry_opts) as ydl:
                        ydl.download([target_url])
                    return
                except Exception as fb_err:
                    if "TASK_CANCELLED" in str(fb_err):
                        raise fb_err
                    continue

        raise err

def download_video_stream(
    url: str,
    format_id: str,
    output_path: str,
    progress_listener=None,
    task_id: str = "",
    speed_mode: str = "SAFE_BALANCED"
) -> str:
    """
    Downloads media with optimized format selection, real-time speed & ETA,
    HTTP 416 auto-recovery, original audio priority, and full resume/cancellation support.
    """
    clean_url = sanitize_url(url)
    parent_dir = os.path.dirname(output_path)
    if parent_dir and not os.path.exists(parent_dir):
        os.makedirs(parent_dir, exist_ok=True)

    reset_task_cancellation(task_id)

    is_yt = is_youtube_url(clean_url)
    if speed_mode == "HIGH_SPEED":
        buf_size = 1048576
        conc_frags = 6
        yt_chunk = 10485760
    elif speed_mode == "DATA_SAVER":
        buf_size = 262144
        conc_frags = 2
        yt_chunk = 2097152
    else:
        buf_size = 524288
        conc_frags = 4
        yt_chunk = 5242880

    base_ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'socket_timeout': 30,
        'retries': 10,
        'fragment_retries': 10,
        'continuedl': True,
        'nocheckcertificate': True,
        'buffersize': buf_size,
        'concurrent_fragment_downloads': conc_frags if is_yt else 1,
        'http_headers': _build_base_headers(),
    }

    # IMPORTANT: Only apply http_chunk_size on YouTube! TikTok/Instagram/FB CDNs reject Range chunks with HTTP 416.
    if is_yt:
        base_ydl_opts['http_chunk_size'] = yt_chunk

    try:
        if '+' in format_id:
            v_id, a_id = format_id.split('+', 1)
            video_temp_path = output_path + ".video.mp4"
            audio_temp_path = output_path + ".audio.m4a"

            state = {
                'video_done_bytes': 0,
                'video_total_bytes': 0,
                'audio_total_bytes': 0
            }

            def video_hook(d):
                if task_id and is_task_cancelled(task_id):
                    raise Exception(f"TASK_CANCELLED:{task_id}")
                if not progress_listener:
                    return
                status = d.get('status')
                if status == 'downloading':
                    downloaded = d.get('downloaded_bytes') or 0
                    total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                    speed = d.get('speed') or 0
                    eta_sec = d.get('eta') or 0
                    if total > 0:
                        state['video_total_bytes'] = total
                    eta_str = format_eta(eta_sec)
                    try:
                        progress_listener.onProgress(int(downloaded), int(total), float(speed or 0), eta_str, 1)
                    except Exception:
                        pass
                elif status == 'finished':
                    state['video_done_bytes'] = state['video_total_bytes']

            v_opts = dict(base_ydl_opts)
            v_opts['format'] = f"{v_id}/bestvideo[ext=mp4]/bestvideo/best"
            v_opts['outtmpl'] = video_temp_path
            v_opts['progress_hooks'] = [video_hook] if progress_listener else []

            if task_id and is_task_cancelled(task_id):
                return json.dumps({"status": "aborted", "task_id": task_id})

            _execute_ydl_with_recovery(
                v_opts, clean_url, [video_temp_path],
                fallback_formats=["bestvideo[ext=mp4]/bestvideo", "best[height<=720]", "best"]
            )

            final_video_path = video_temp_path
            if not os.path.exists(video_temp_path):
                base_v = os.path.splitext(video_temp_path)[0]
                for ext in ['.mp4', '.mkv', '.webm']:
                    if os.path.exists(base_v + ext):
                        final_video_path = base_v + ext
                        break

            v_file_size = os.path.getsize(final_video_path) if os.path.exists(final_video_path) else state['video_total_bytes']
            state['video_done_bytes'] = v_file_size

            if task_id and is_task_cancelled(task_id):
                return json.dumps({"status": "aborted", "task_id": task_id})

            def audio_hook(d):
                if task_id and is_task_cancelled(task_id):
                    raise Exception(f"TASK_CANCELLED:{task_id}")
                if not progress_listener:
                    return
                if d.get('status') == 'downloading':
                    a_downloaded = d.get('downloaded_bytes') or 0
                    a_total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                    speed = d.get('speed') or 0
                    eta_sec = d.get('eta') or 0
                    cumulative_downloaded = state['video_done_bytes'] + a_downloaded
                    cumulative_total = state['video_done_bytes'] + (a_total if a_total > 0 else 0)
                    eta_str = format_eta(eta_sec)
                    try:
                        progress_listener.onProgress(int(cumulative_downloaded), int(cumulative_total), float(speed or 0), eta_str, 2)
                    except Exception:
                        pass

            a_opts = dict(base_ydl_opts)
            a_opts['format'] = f"{a_id}/bestaudio[format_id*=-orig]/bestaudio[ext=m4a]/bestaudio/best"
            a_opts['outtmpl'] = audio_temp_path
            a_opts['progress_hooks'] = [audio_hook] if progress_listener else []

            _execute_ydl_with_recovery(
                a_opts, clean_url, [audio_temp_path],
                fallback_formats=["bestaudio[ext=m4a]", "bestaudio", "best"]
            )

            final_audio_path = audio_temp_path
            if not os.path.exists(audio_temp_path):
                base_a = os.path.splitext(audio_temp_path)[0]
                for ext in ['.m4a', '.mp3', '.aac', '.webm', '.opus']:
                    if os.path.exists(base_a + ext):
                        final_audio_path = base_a + ext
                        break

            if os.path.exists(final_video_path) and os.path.exists(final_audio_path):
                return json.dumps({
                    "status": "mux_needed",
                    "video_path": final_video_path,
                    "audio_path": final_audio_path,
                    "output_path": output_path
                })
            elif os.path.exists(final_video_path):
                return json.dumps({
                    "status": "success",
                    "file_path": final_video_path
                })
            else:
                return json.dumps({
                    "status": "error",
                    "error_code": "general",
                    "message": "Could not save the video streams."
                })

        else:
            def single_hook(d):
                if task_id and is_task_cancelled(task_id):
                    raise Exception(f"TASK_CANCELLED:{task_id}")
                if not progress_listener:
                    return
                if d.get('status') == 'downloading':
                    downloaded = d.get('downloaded_bytes') or 0
                    total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                    speed = d.get('speed') or 0
                    eta_sec = d.get('eta') or 0
                    eta_str = format_eta(eta_sec)
                    try:
                        progress_listener.onProgress(int(downloaded), int(total), float(speed or 0), eta_str, 0)
                    except Exception:
                        pass

            if format_id and format_id not in ['best', 'b']:
                chosen_format = f"{format_id}/best[ext=mp4]/best"
            else:
                chosen_format = 'best[height<=720][ext=mp4]/best[height<=720]/best'

            s_opts = dict(base_ydl_opts)
            s_opts['format'] = chosen_format
            s_opts['outtmpl'] = output_path
            s_opts['progress_hooks'] = [single_hook] if progress_listener else []

            if task_id and is_task_cancelled(task_id):
                return json.dumps({"status": "aborted", "task_id": task_id})

            _execute_ydl_with_recovery(
                s_opts, clean_url, [output_path],
                fallback_formats=["best[ext=mp4]", "best"]
            )

            final_path = output_path
            if not os.path.exists(output_path):
                base_without_ext = os.path.splitext(output_path)[0]
                for ext in ['.mp4', '.mkv', '.webm', '.m4a', '.mp3']:
                    candidate = base_without_ext + ext
                    if os.path.exists(candidate):
                        final_path = candidate
                        break

            if os.path.exists(final_path) and os.path.getsize(final_path) > 0:
                return json.dumps({"status": "success", "file_path": final_path})
            else:
                return json.dumps({
                    "status": "error",
                    "error_code": "general",
                    "message": "Failed to save downloaded media file."
                })

    except Exception as e:
        err_msg = str(e)
        if "TASK_CANCELLED" in err_msg:
            return json.dumps({"status": "aborted", "task_id": task_id})
        err_code, human_msg = classify_error(err_msg)
        return json.dumps({
            "status": "error",
            "error_code": err_code,
            "is_network_error": err_code in ("network", "timeout"),
            "message": human_msg
        })

# Backward compatibility alias
download_media_format = download_video_stream
