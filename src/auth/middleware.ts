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
    await request.jwtVerify();
  } catch {
    return reply.status(401).send({ message: 'Unauthorized' });
  }
}
