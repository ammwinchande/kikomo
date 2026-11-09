package co.tz.kikomo.web;

import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.shared.error.NotFoundException;
import co.tz.kikomo.web.dto.CallView;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallRepository calls;

    public CallController(CallRepository calls) {
        this.calls = calls;
    }

    /**
     * The 100 most recent calls, optionally for one agent.
     */
    @GetMapping
    public List<CallView> list(@RequestParam(value = "agentId", required = false) UUID agentId) {
        return calls.findRecent(agentId).stream().map(CallView::of).toList();
    }

    @GetMapping("/{id}")
    public CallView get(@PathVariable("id") UUID id) {
        return calls.findById(id).map(CallView::of).orElseThrow(() -> new NotFoundException("call " + id + " not found"));
    }
}
