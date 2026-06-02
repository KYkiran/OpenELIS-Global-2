package org.openelisglobal.atomfeed.transaction;

import org.ict4h.atomfeed.transaction.AFTransactionManager;
import org.ict4h.atomfeed.transaction.AFTransactionWork;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SpringAFTransactionManager implements AFTransactionManager {

    private final TransactionTemplate transactionTemplate;

    public SpringAFTransactionManager(PlatformTransactionManager txManager) {
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public <T> T executeWithTransaction(AFTransactionWork<T> action) throws RuntimeException {
        return transactionTemplate.execute(status -> action.execute());
    }
}