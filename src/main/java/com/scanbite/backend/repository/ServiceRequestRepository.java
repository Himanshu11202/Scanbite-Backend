package com.scanbite.backend.repository;

import com.scanbite.backend.model.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByCafe_Id(Long cafeId);
    List<ServiceRequest> findByCafe_IdAndStatus(Long cafeId, String status);
}
