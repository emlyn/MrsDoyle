// Load the Visualization API and the piechart package.
google.load('visualization', '1.0', {'packages':['corechart', 'annotatedtimeline']});

// Set a callback to run when the Google Visualization API is loaded.
google.setOnLoadCallback(drawCharts);

// Callback that creates and populates our charts
function drawCharts() {
    var options = {title: 'Who has drunk the most tea via Mrs Doyle (all time)?',
                   width: 1000,
                   height: 500};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('string', 'Drinker');
    data.addColumn('number', 'Cups drunk');

    $.ajax({
        url: "/drinker-cups",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_drunk_chart = new google.visualization.PieChart(document.getElementById('all_time_drunk_div'));
    all_time_drunk_chart.draw(data, options);

    // ----------------------

    var options = {title: 'Who has been luckiest so far (and so is more likely to get picked next)?',
                   width: 1000,
                   height: 500,
                   interpolateNulls: true,
                   series: {
                       0: {type: 'bars'},
                       1: {type: 'line', visibleInLegend: false}},
                   vAxis: {minValue: 0},
                   hAxis: {viewWindow: {min: 1}}};

    // Create the data table.
    data = new google.visualization.DataTable();
    data.addColumn('string', 'Drinker');
    data.addColumn('number', 'Relative probability');
    data.addColumn('number', 'Fixed height line');

    $.ajax({
        url: "/drinker-luck",
        dataType: "json",
        async: false
    }).done(function(json) {
        for (var i in json) {
            json[i].push(null)
        }
        json.unshift([null, null, 1]);
        json.push([null, null, 1]);
        options.hAxis.viewWindow.max = json.length - 1
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var luckiest_chart = new google.visualization.ComboChart(document.getElementById('luckiest_so_far_div'));
    luckiest_chart.draw(data, options);

    // --------------------

    var options = {width: 1000,
                   height: 400,
                   fill: 50,
                   min: 0};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('date', 'Date');
    data.addColumn('number', 'Rounds');
    data.addColumn('number', 'Cups');

    $.ajax({
        url: "/recent-drinkers",
        dataType: "json",
        async: false
    }).done(function(json) {
        var points = []
        for (i in json) {
            var d = json[i][0];
            var v = json[i].slice(1);
            var z = v.map(function(x){return 0;});
            points.push([new Date(d[0],d[1],d[2],0,0,0,0)].concat(z));
            points.push([new Date(d[0],d[1],d[2],0,0,0,0)].concat(v));
            points.push([new Date(d[0],d[1],d[2],23,59,59,999)].concat(v));
            points.push([new Date(d[0],d[1],d[2],23,59,59,999)].concat(z));
        }
        data.addRows(points);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_drunk_chart = new google.visualization.AnnotatedTimeLine(document.getElementById('last_week_drunk_div'));
    all_time_drunk_chart.draw(data, options);
}
