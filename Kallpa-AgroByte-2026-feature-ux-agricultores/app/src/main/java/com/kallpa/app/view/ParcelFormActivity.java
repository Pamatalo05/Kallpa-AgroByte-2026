package com.kallpa.app.view;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import com.kallpa.app.databinding.ActivityParcelFormBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ParcelFormActivity extends ActividadBase {

    private ActivityParcelFormBinding binding;

    // Datos que enviaremos a la pantalla de ubicación
    public static final String EXTRA_NOMBRE = "parcela_nombre";
    public static final String EXTRA_CULTIVO = "parcela_cultivo";
    public static final String EXTRA_SIEMBRA = "parcela_siembra";
    public static final String EXTRA_COSECHA = "parcela_cosecha";
    public static final String EXTRA_RIEGO = "parcela_riego";
    public static final String EXTRA_HECTAREAS = "parcela_hectareas";

    private LocalDate fechaSiembra;
    private LocalDate fechaCosecha;

    private final DateTimeFormatter formato =
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy",
                    Locale.forLanguageTag("es-EC")
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityParcelFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configurarCultivos();

        if (savedInstanceState != null) {
            restaurarFechas(savedInstanceState);
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.inputSowingDate.setOnClickListener(
                v -> seleccionarFecha(true)
        );

        binding.inputHarvestDate.setOnClickListener(
                v -> seleccionarFecha(false)
        );

        binding.btnNextLocation.setOnClickListener(
                v -> continuar()
        );
    }

    // Lista de cultivos
    private void configurarCultivos() {

        String[] cultivos = {
                "Selecciona un cultivo",
                "Arroz",
                "Maíz duro",
                "Soya",
                "Fréjol caupí",
                "Maní",
                "Mucuna"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cultivos
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        binding.spinnerCrop.setAdapter(adapter);
    }

    // Mostrar calendario
    private void seleccionarFecha(boolean esSiembra) {

        LocalDate fechaInicial = esSiembra
                ? fechaSiembra
                : fechaCosecha;

        if (fechaInicial == null) {
            fechaInicial = LocalDate.now();
        }

        DatePickerDialog calendario = new DatePickerDialog(
                this,
                (view, anio, mes, dia) -> {

                    LocalDate fecha = LocalDate.of(
                            anio,
                            mes + 1,
                            dia
                    );

                    if (esSiembra) {
                        fechaSiembra = fecha;

                        binding.inputSowingDate.setText(
                                fecha.format(formato)
                        );

                    } else {
                        fechaCosecha = fecha;

                        binding.inputHarvestDate.setText(
                                fecha.format(formato)
                        );
                    }
                },
                fechaInicial.getYear(),
                fechaInicial.getMonthValue() - 1,
                fechaInicial.getDayOfMonth()
        );

        // La última siembra no puede estar en el futuro
        if (esSiembra) {
            calendario.getDatePicker().setMaxDate(
                    System.currentTimeMillis()
            );
        }

        calendario.show();
    }

    // Validar formulario y continuar
    private void continuar() {

        String nombre = binding.inputParcelName
                .getText().toString().trim();

        if (nombre.isEmpty()) {
            binding.inputParcelName.setError(
                    "Ingresa el nombre de tu parcela"
            );
            binding.inputParcelName.requestFocus();
            return;
        }

        if (binding.spinnerCrop.getSelectedItemPosition() == 0) {
            mensaje("Selecciona el cultivo actual.");
            return;
        }

        if (fechaSiembra == null) {
            mensaje("Selecciona la fecha de última siembra.");
            return;
        }

        if (fechaCosecha == null) {
            mensaje("Selecciona la fecha de cosecha.");
            return;
        }

        if (fechaCosecha.isBefore(fechaSiembra)) {
            mensaje("La cosecha no puede ser anterior a la siembra.");
            return;
        }

        int opcionRiego =
                binding.groupIrrigation.getCheckedRadioButtonId();

        if (opcionRiego == -1) {
            mensaje("Indica si tu parcela dispone de riego.");
            return;
        }

        String textoHectareas = binding.inputHectares
                .getText().toString().trim()
                .replace(",", ".");

        double hectareas;

        try {
            hectareas = Double.parseDouble(textoHectareas);
        } catch (NumberFormatException e) {
            binding.inputHectares.setError(
                    "Ingresa una superficie válida"
            );
            binding.inputHectares.requestFocus();
            return;
        }

        if (!Double.isFinite(hectareas)
                || hectareas < 1
                || hectareas > 50) {

            binding.inputHectares.setError(
                    "El piloto admite parcelas de 1 a 50 hectáreas"
            );

            binding.inputHectares.requestFocus();
            return;
        }

        boolean tieneRiego =
                opcionRiego == binding.radioYes.getId();

        String cultivo = binding.spinnerCrop
                .getSelectedItem().toString();

        // Pasar el formulario al paso de ubicación
        Intent intent = new Intent(
                this,
                LocationPermissionActivity.class
        );

        intent.putExtra(EXTRA_NOMBRE, nombre);
        intent.putExtra(EXTRA_CULTIVO, cultivo);

        intent.putExtra(
                EXTRA_SIEMBRA,
                fechaSiembra.toString()
        );

        intent.putExtra(
                EXTRA_COSECHA,
                fechaCosecha.toString()
        );

        intent.putExtra(EXTRA_RIEGO, tieneRiego);
        intent.putExtra(EXTRA_HECTAREAS, hectareas);

        startActivity(intent);
    }

    // Conservar fechas al recrear la pantalla
    @Override
    protected void onSaveInstanceState(Bundle outState) {

        super.onSaveInstanceState(outState);

        if (fechaSiembra != null) {
            outState.putString(
                    "fechaSiembra",
                    fechaSiembra.toString()
            );
        }

        if (fechaCosecha != null) {
            outState.putString(
                    "fechaCosecha",
                    fechaCosecha.toString()
            );
        }
    }

    private void restaurarFechas(Bundle state) {

        String siembra = state.getString("fechaSiembra");
        String cosecha = state.getString("fechaCosecha");

        if (siembra != null) {
            fechaSiembra = LocalDate.parse(siembra);

            binding.inputSowingDate.setText(
                    fechaSiembra.format(formato)
            );
        }

        if (cosecha != null) {
            fechaCosecha = LocalDate.parse(cosecha);

            binding.inputHarvestDate.setText(
                    fechaCosecha.format(formato)
            );
        }
    }
}

