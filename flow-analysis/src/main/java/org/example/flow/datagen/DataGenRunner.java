package org.example.flow.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.datagen", name = "auto-init", havingValue = "true")
public class DataGenRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenRunner.class);

    private final DataGenerator dataGenerator;

    public DataGenRunner(DataGenerator dataGenerator) {
        this.dataGenerator = dataGenerator;
    }

    @Override
    public void run(String... args) {
        GenResult r = dataGenerator.generate();
        String marker = String.format(
                "DATAGEN_DONE seed=%d userCount=%d profileRows=%d dwdRows=%d dwsHour=%d dwsDay=%d elapsedMs=%d",
                r.seed(), r.userCount(), r.profileRows(), r.dwdRows(),
                r.dwsHourRows(), r.dwsDayRows(), r.elapsedMs());
        log.info(marker);
        System.out.println(marker);
    }
}
