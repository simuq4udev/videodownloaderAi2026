# Facebook Authorized Download Integration (Compliant Path)

If you paste a Facebook share URL such as:

- `https://www.facebook.com/share/v/...`

the app blocks it because it is a webpage URL, not a direct media file URL.

## Why direct download fails
1. Share/page URLs are HTML pages, not static `.mp4` links.
2. Access may require login/session checks.
3. Store and platform policies require authorized API-based access.

## Recommended architecture

1. Android app sends a Facebook content reference to your backend.
2. Backend uses Meta Graph API with approved scopes.
3. Backend returns metadata/authorized media URL if user/content is permitted.
4. Android downloads only authorized URL.

## Minimal backend endpoints

- `POST /api/v1/facebook/resolve`
  - request: `{ "shareUrl": "..." }`
  - response: `{ "status": "authorized|forbidden|unsupported", "title": "...", "downloadUrl": "..." }`

- `POST /api/v1/facebook/download`
  - request: `{ "assetId": "..." }`
  - response: signed URL for temporary download

## Android integration steps

1. Add a `FacebookResolverApi` Retrofit interface.
2. On blocked Facebook URL, call resolve endpoint instead of direct DownloadManager URL path.
3. If authorized URL returned, enqueue download with existing flow.
4. If not authorized, show explicit policy message.

## Security checklist

- Keep app secret on backend only.
- Use short-lived signed URLs.
- Log consent + ownership checks.
- Rate limit resolve/download endpoints.

