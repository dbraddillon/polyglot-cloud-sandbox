# Pinned to the same version as package.json's "volta" field and .nvmrc, for the same reason a
# real project keeps its container's runtime version in sync with what local dev tooling
# (volta/nvm) pins - three separate places asserting the same fact, not three independent
# choices. Single-stage is fine here (unlike task-api's multi-stage Maven build): npm install
# doesn't need a separate toolchain the final image has to avoid carrying around the way a JDK
# does.
FROM node:22.11.0-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY index.js ./
EXPOSE 8086
CMD ["node", "index.js"]
