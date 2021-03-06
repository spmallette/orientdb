package com.orientechnologies.orient.test.database.auto;

import com.orientechnologies.orient.object.db.OObjectDatabaseTx;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 7/3/14
 */
@Test
public class ObjectDBBaseTest extends BaseTest<OObjectDatabaseTx> {
	@Parameters(value = "url")
	public ObjectDBBaseTest(@Optional String url) {
		super(url);
	}

	@Override
	protected OObjectDatabaseTx createDatabaseInstance(String url) {
		return new OObjectDatabaseTx(url);
	}
}