java \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
-Daeron.dir=/tmp/aeron \
-Daeron.dir.delete.on.start=true \
-Daeron.print.configuration=true \
-Daeron.event.log=true \
-Daeron.client.liveness.timeout=10000000000 \
-Daeron.driver.timeout=60000 \
-cp aeron-all-1.44.0.jar io.aeron.driver.MediaDriver