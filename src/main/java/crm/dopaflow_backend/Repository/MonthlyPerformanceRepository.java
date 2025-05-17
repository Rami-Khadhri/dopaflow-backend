package crm.dopaflow_backend.Repository;

import crm.dopaflow_backend.Model.MonthlyPerformance;
import crm.dopaflow_backend.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyPerformanceRepository extends JpaRepository<MonthlyPerformance, Long> {
    Optional<MonthlyPerformance> findByUserAndYearAndMonth(User user, int year, int month);
    List<MonthlyPerformance> findByUser(User user);
    List<MonthlyPerformance> findByYearAndMonth(int year, int month);
}