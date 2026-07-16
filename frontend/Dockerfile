# ---------- 构建阶段：Node 构建静态资源 ----------
FROM node:24-slim AS build
WORKDIR /app
# 先拷贝依赖清单，利用缓存
COPY package.json package-lock.json ./
RUN npm ci
# 再拷贝源码构建
COPY . .
RUN npm run build

# ---------- 运行阶段：Nginx 托管静态资源 + 反代 /api ----------
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
