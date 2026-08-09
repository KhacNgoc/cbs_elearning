# Build stage
FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_URL
ARG VITE_USE_MOCK_API
ARG VITE_RAG_CHAT_TIMEOUT_MS
ARG VITE_QUIZ_GENERATION_TIMEOUT_MS
ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_USE_MOCK_API=${VITE_USE_MOCK_API}
ENV VITE_RAG_CHAT_TIMEOUT_MS=${VITE_RAG_CHAT_TIMEOUT_MS}
ENV VITE_QUIZ_GENERATION_TIMEOUT_MS=${VITE_QUIZ_GENERATION_TIMEOUT_MS}
COPY package*.json ./
RUN npm install
ARG CACHEBUST=1
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
