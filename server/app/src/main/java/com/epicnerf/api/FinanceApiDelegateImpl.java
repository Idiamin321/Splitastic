package com.epicnerf.api;

import com.epicnerf.hibernate.MapToHibernateModel;
import com.epicnerf.hibernate.MapToOpenApiModel;
import com.epicnerf.hibernate.model.FinanceEntry;
import com.epicnerf.hibernate.model.User;
import com.epicnerf.hibernate.repository.FinanceRepository;
import com.epicnerf.model.FinanceEntryEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.persistence.NoResultException;
import java.util.Optional;

@Service
public class FinanceApiDelegateImpl implements FinanceApiDelegate {

    @Autowired
    private ApiSupport apiSupport;

    @Autowired
    private FinanceRepository financeRepository;

    @Autowired
    private MapToHibernateModel mapper;

    @Autowired
    private MapToOpenApiModel openApiMapper;

    public ResponseEntity<Void> financeFinanceIdDelete(Integer financeId) {
        User user = apiSupport.getCurrentUser();
        Optional<FinanceEntry> finance = financeRepository.findById(financeId);

        if (finance.isPresent() && finance.get().getSpentFrom().getId().equals(user.getId())) {
            financeRepository.delete(finance.get());
            return new ResponseEntity<>(HttpStatus.OK);
        }
        throw new NoResultException();
    }

    public ResponseEntity<com.epicnerf.model.FinanceEntry> financeFinanceIdGet(Integer financeId) {
        User user = apiSupport.getCurrentUser();
        Optional<FinanceEntry> finance = financeRepository.findById(financeId);
        if (finance.isPresent()) {
            apiSupport.validateUserIsInGroup(finance.get().getGroup(), user.getId());
            com.epicnerf.model.FinanceEntry entry = openApiMapper.map(finance.get());
            return ResponseEntity.ok(entry);
        }

        throw new NoResultException();
    }

    public ResponseEntity<Void> financePut(com.epicnerf.model.FinanceEntry financeEntry) {
        User user = apiSupport.getCurrentUser();
        Optional<FinanceEntry> finance = financeRepository.findById(financeEntry.getId());
        if (finance.isPresent() && finance.get().getSpentFrom().getId().equals(user.getId())) {
            FinanceEntry f = mapper.mapFinance(financeEntry, true);
            f.setGroup(finance.get().getGroup());
            apiSupport.validateUserIsInGroup(finance.get().getGroup(), financeEntry.getSpentFrom());
            for (FinanceEntryEntry entry : financeEntry.getSpent()) {
                apiSupport.validateUserIsInGroup(finance.get().getGroup(), entry.getSpentFor());
            }

            financeRepository.save(f);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        throw new NoResultException();
    }
}
