import { NextRequest } from 'next/server';

const BACKEND_URL = process.env.API_BACKEND_URL || 'http://localhost:8080';

async function handler(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const joinedPath = path.join('/');

  // Forward query parameters from the original request
  const searchParams = req.nextUrl.searchParams.toString();
  const queryString = searchParams ? `?${searchParams}` : '';
  const targetUrl = `${BACKEND_URL}/api/v1/${joinedPath}${queryString}`;

  let body: string | undefined;

  try {
    body = await req.text();
  } catch {
    body = undefined;
  }

  let upstreamResponse: Response;

  try {
    upstreamResponse = await fetch(targetUrl, {
      method: req.method,
      headers: {
        'Content-Type': req.headers.get('content-type') || 'application/json',
        ...(req.headers.get('authorization')
          ? { Authorization: req.headers.get('authorization') as string }
          : {}),
      },
      body: req.method !== 'GET' && req.method !== 'HEAD' ? body : undefined,
      cache: 'no-store',
    });
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : 'Upstream request failed';
    return Response.json({ message }, { status: 503 });
  }

  const responseText = await upstreamResponse.text();
  const contentType =
    upstreamResponse.headers.get('content-type') || 'application/json';

  return new Response(responseText, {
    status: upstreamResponse.status,
    headers: {
      'Content-Type': contentType,
    },
  });
}

export async function GET(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
) {
  return handler(req, ctx);
}

export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
) {
  return handler(req, ctx);
}

export async function PUT(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
) {
  return handler(req, ctx);
}

export async function DELETE(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
) {
  return handler(req, ctx);
}

export async function PATCH(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> }
) {
  return handler(req, ctx);
}
