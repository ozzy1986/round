import type { FastifyRequest, FastifyReply } from 'fastify';

export type AuthenticatedRequest = FastifyRequest & { user: { id: string } };

export function getUserId(req: AuthenticatedRequest): string {
  return req.user.id;
}

export async function authVerify(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  try {
    const decoded = await request.jwtVerify<{ sub: string }>();
    (request as AuthenticatedRequest).user = {
      id: decoded.sub,
    };
  } catch {
    await reply.status(401).send({ message: 'Unauthorized' });
  }
}
