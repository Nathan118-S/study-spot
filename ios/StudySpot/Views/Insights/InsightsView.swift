import SwiftUI
import Charts

struct InsightsView: View {
    @Environment(DataStore.self) private var data

    var body: some View {
        let weekly = Insights.weekly(data.assignments)
        let total = weekly.last?.cumulative ?? 0
        let thisWeek = weekly.last?.completed ?? 0
        let avg = weekly.isEmpty ? 0 : Double(weekly.reduce(0) { $0 + $1.completed }) / Double(weekly.count)
        let best = weekly.map(\.completed).max() ?? 0

        ScrollView {
            VStack(spacing: 16) {
                Text("Track your assignment completion progress over time.")
                    .font(.subheadline).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    StatCard(label: "Total completed", value: "\(total)", symbol: "checkmark.circle.fill", symbolTint: .green)
                    StatCard(label: "This week", value: "\(thisWeek)", symbol: "calendar.badge.checkmark", symbolTint: .accentColor)
                    StatCard(label: "Avg per week", value: String(format: "%.1f", avg), symbol: "waveform.path.ecg", symbolTint: .blue)
                    StatCard(label: "Best week", value: "\(best)", symbol: "rosette", symbolTint: .yellow)
                }

                Card {
                    Text("Weekly completion trend").font(.headline)
                    Text("Assignments marked complete each week (last \(Insights.weeks) weeks).")
                        .font(.caption).foregroundStyle(.secondary)
                    Chart(weekly) { b in
                        AreaMark(x: .value("Week", b.label), y: .value("Completed", b.completed))
                            .interpolationMethod(.monotone)
                            .foregroundStyle(LinearGradient(colors: [Color.accentColor.opacity(0.35), Color.accentColor.opacity(0)],
                                                            startPoint: .top, endPoint: .bottom))
                        LineMark(x: .value("Week", b.label), y: .value("Completed", b.completed))
                            .interpolationMethod(.monotone)
                            .foregroundStyle(Color.accentColor)
                            .lineStyle(StrokeStyle(lineWidth: 2))
                    }
                    .frame(height: 220)
                }

                Card {
                    Text("Completions per week").font(.headline)
                    Text("Bar view of the same trend, highlighting the current week.")
                        .font(.caption).foregroundStyle(.secondary)
                    Chart(Array(weekly.enumerated()), id: \.element.id) { i, b in
                        BarMark(x: .value("Week", b.label), y: .value("Completed", b.completed))
                            .foregroundStyle(i == weekly.count - 1 ? Color.accentColor : Color.accentColor.opacity(0.5))
                            .cornerRadius(4)
                    }
                    .frame(height: 220)
                }
            }
            .padding()
        }
        .navigationTitle("Insights")
        .refreshable { await data.reloadData() }
    }
}
