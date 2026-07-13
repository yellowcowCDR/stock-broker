package com.hermes.broker.market.application.port.out;

import com.hermes.broker.market.domain.FinancialStatement;
import java.util.List;

public interface LoadFinancialStatementPort {
    List<FinancialStatement> loadRecentFinancialStatements(String corpCode);
}
