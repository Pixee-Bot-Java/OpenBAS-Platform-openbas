package io.openex.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V2_30__Document_path extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Statement select = context.getConnection().createStatement();
        select.execute("ALTER TABLE documents ADD document_target text;");
        select.executeUpdate("UPDATE documents SET document_target=document_name;");
    }
}
