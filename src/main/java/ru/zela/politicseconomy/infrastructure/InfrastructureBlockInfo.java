package ru.zela.politicseconomy.infrastructure;

public record InfrastructureBlockInfo(
    String blockId,
    InfrastructureCategory category,
    double baseMaintenance
) {}
