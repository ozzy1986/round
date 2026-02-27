import type { FastifyRequest, FastifyReply } from 'fastify';

export async function authVerify(
  request: FastifyRequest,
  reply: FastifyReply
): Promise<void> {
  try {
    const decoded = await request.jwtVerify<{ sub: string }>();
    (request as FastifyRequest & { user: { id: string } }).user = {
      id: decoded.sub,
    };
  } catch {
    await reply.status(401).send({ message: 'Unauthorized' });
  }
}
