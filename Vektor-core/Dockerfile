FROM node:22-bookworm-slim

WORKDIR /app

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=10000

COPY package*.json ./
RUN npm install --omit=dev

COPY . .

EXPOSE 10000

CMD ["npm", "start"]