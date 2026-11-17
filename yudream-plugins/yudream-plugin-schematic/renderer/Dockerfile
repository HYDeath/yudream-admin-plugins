FROM registry.yudream.online/yda/render-server:latest
WORKDIR /app/schematic
COPY server.mjs validation.mjs ./
COPY dist/ ./dist/
COPY assets/ ./assets/
COPY LICENSE-DEEPSLATE.txt LICENSE-GL-MATRIX.txt ./
ENV SCHEMATIC_RENDER_PORT=3000
EXPOSE 3000
USER pwuser
CMD ["node", "server.mjs"]
