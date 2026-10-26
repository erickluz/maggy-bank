FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY angular.json tsconfig*.json ./
COPY src src
RUN npm run build
FROM nginx:1.27-alpine
COPY --from=build /app/dist/maggy-bank/browser /usr/share/nginx/html
