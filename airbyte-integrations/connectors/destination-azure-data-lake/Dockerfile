FROM eclipse-temurin:21-jre-jammy
WORKDIR /airbyte

# Incrementing to version 8 to bust the layer cache completely
RUN echo "Busting cache for version 8"

COPY airbyte-app.tar /airbyte/app.tar
RUN tar xf app.tar --strip-components=1 && rm app.tar
ENV AIRBYTE_ENTRYPOINT="/airbyte/bin/destination-azure-data-lake"
ENTRYPOINT ["/airbyte/bin/destination-azure-data-lake"]