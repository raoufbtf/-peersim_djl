package com.example.peersimdjl.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@WebMvcTest(SimulationController.class)
public class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimulationService simulationService;

    @Test
    void postStartValidBody_returns200() throws Exception {
        doNothing().when(simulationService).start(any(SimulationRequest.class));

        String validBody = "{\"modelType\":\"MLP\","
                + "\"datasetPaths\":[\"C:/Users/Admin/Desktop/-peersim_djl/src/main/resources/adult.csv\"],"
                + "\"networkSize\":1,"
                + "\"sessionRequirements\":[1],"
                + "\"federatedEpochs\":1,"
                + "\"learningRate\":0.001,"
                + "\"batchStrategy\":\"ROUND_ROBIN\","
                + "\"maxBatchesPerNode\":1,"
                + "\"preprocessOnUpload\":true,"
                + "\"simulationCycles\":1}";

        mockMvc.perform(MockMvcRequestBuilders.post("/api/simulations/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("started"));
    }

    @Test
    void postStartEmptyBody_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/simulations/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void postStopWhenIdle_returns409() throws Exception {
        doThrow(new IllegalStateException("Not running")).when(simulationService).stop();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/simulations/stop"))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.error").value("Not running"));
    }

    @Test
    void getStatus_returns200WithStateField() throws Exception {
        when(simulationService.getState()).thenReturn(SimulationState.IDLE);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/simulations/status"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("IDLE"));
    }
}
